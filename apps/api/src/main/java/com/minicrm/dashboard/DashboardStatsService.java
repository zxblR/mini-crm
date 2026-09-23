package com.minicrm.dashboard;

import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.JdbcTimeUtils;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardStatsService {
  private static final int MAX_RANGE_DAYS = 366;
  private final JdbcTemplate jdbc;

  public DashboardStatsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public StatsOverviewDTO overview(SecurityUser actor, Filter raw) {
    Filter filter = normalize(actor, raw);
    List<Object> overviewArgs = new ArrayList<>(List.of(
        dbTime(filter.from()), dbTime(filter.to()),
        dbTime(filter.from()), dbTime(filter.to()),
        dbTime(filter.from()), dbTime(filter.to()), actor.organizationId()));
    addOwner(overviewArgs, filter);
    OverviewRow row = jdbc.queryForObject("""
        SELECT COUNT(*) AS total,
          COUNT(*) FILTER (WHERE created_at >= ? AND created_at < ?) AS new_count,
          COUNT(*) FILTER (WHERE created_at >= ? AND created_at < ?
            AND status = 'won'::lead_status AND closed_at IS NOT NULL) AS won_count,
          AVG(EXTRACT(EPOCH FROM (closed_at - created_at))::double precision)
            FILTER (WHERE created_at >= ? AND created_at < ?
              AND status = 'won'::lead_status AND closed_at IS NOT NULL
              AND closed_at >= created_at) AS avg_cycle
        FROM leads WHERE organization_id = ? AND archived_at IS NULL
        """ + ownerClause(filter, ""),
        (rs, rowNum) -> new OverviewRow(rs.getLong("total"), rs.getLong("new_count"),
            rs.getLong("won_count"), rs.getObject("avg_cycle", Double.class)), overviewArgs.toArray());
    List<StatsOverviewDTO.StatusCountDTO> queriedStatusCounts = jdbc.query("""
        SELECT status::text AS status, COUNT(*) AS count
        FROM leads
        WHERE organization_id = ? AND archived_at IS NULL
        """ + ownerClause(filter, "") + " GROUP BY status ORDER BY status",
        (rs, rowNum) -> new StatsOverviewDTO.StatusCountDTO(rs.getString("status"), rs.getLong("count")),
        filter.ownerId() == null ? new Object[] {actor.organizationId()} : new Object[] {actor.organizationId(), filter.ownerId()});
    List<StatsOverviewDTO.StatusCountDTO> statusCounts = new ArrayList<>();
    for (String status : List.of("new", "contacted", "qualified", "proposal", "negotiation", "won", "lost")) {
      long count = queriedStatusCounts.stream().filter(item -> item.status().equals(status))
          .mapToLong(StatsOverviewDTO.StatusCountDTO::count).findFirst().orElse(0);
      statusCounts.add(new StatsOverviewDTO.StatusCountDTO(status, count));
    }
    ResponseSample response = responseSample(actor, filter);
    double conversion = row.newCount() == 0 ? 0 : (double) row.won() / row.newCount();
    return new StatsOverviewDTO(row.total(), row.newCount(), statusCounts, conversion,
        row.avgCycle(), response.responded(), response.unresponded(), response.avgSeconds());
  }

  public PageSupport.Result<ChannelStatsDTO> channels(SecurityUser actor, Filter raw, Integer page, Integer pageSize) {
    Filter filter = normalize(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    Object[] args = outcomeArgs(actor, filter);
    Long total = jdbc.queryForObject("""
        SELECT COUNT(DISTINCT source) FROM leads
        WHERE organization_id = ? AND archived_at IS NULL AND created_at >= ? AND created_at < ?
        """ + ownerClause(filter, ""), Long.class, args);
    List<Object> listArgs = append(args, paging.pageSize(), paging.offset());
    List<ChannelStatsDTO> items = jdbc.query("""
        SELECT source, COUNT(*) AS lead_count,
          COUNT(*) FILTER (WHERE status = 'won'::lead_status AND closed_at IS NOT NULL) AS won_count
        FROM leads
        WHERE organization_id = ? AND archived_at IS NULL AND created_at >= ? AND created_at < ?
        """ + ownerClause(filter, "") + " GROUP BY source ORDER BY lead_count DESC, source LIMIT ? OFFSET ?",
        (rs, rowNum) -> channel(rs), listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public PageSupport.Result<SalesRankingDTO> salesRanking(SecurityUser actor, Filter raw, Integer page, Integer pageSize) {
    Filter filter = normalize(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    Object[] args = outcomeArgs(actor, filter);
    Long total = jdbc.queryForObject("""
        SELECT COUNT(DISTINCT owner_id) FROM leads
        WHERE organization_id = ? AND archived_at IS NULL AND owner_id IS NOT NULL
          AND created_at >= ? AND created_at < ?
        """ + ownerClause(filter, ""), Long.class, args);
    List<Object> listArgs = append(args, actor.organizationId(), dbTime(filter.from()), dbTime(filter.to()),
        actor.organizationId(), paging.pageSize(), paging.offset());
    List<SalesRankingDTO> items = jdbc.query("""
        WITH cohort AS (
          SELECT l.owner_id, l.id, l.status, l.closed_at, l.created_at
          FROM leads l
          WHERE l.organization_id = ? AND l.archived_at IS NULL AND l.owner_id IS NOT NULL
            AND l.created_at >= ? AND l.created_at < ?
        """ + ownerClause(filter, "l") + """
        ), follow_counts AS (
          SELECT l.owner_id, COUNT(f.id) AS follow_count
          FROM cohort l
          JOIN follow_ups f ON f.lead_id = l.id AND f.organization_id = ?
          WHERE f.deleted_at IS NULL AND f.occurred_at >= ? AND f.occurred_at < ?
          GROUP BY l.owner_id
        )
        SELECT c.owner_id, u.name AS owner_name,
          COALESCE(fc.follow_count, 0) AS follow_up_count,
          COUNT(*) FILTER (WHERE c.status = 'won'::lead_status AND c.closed_at IS NOT NULL) AS won_count,
          AVG(EXTRACT(EPOCH FROM (c.closed_at - c.created_at))::double precision)
            FILTER (WHERE c.status = 'won'::lead_status AND c.closed_at IS NOT NULL
              AND c.closed_at >= c.created_at) AS avg_cycle,
          COUNT(*) AS lead_count
        FROM cohort c
        LEFT JOIN users u ON u.id = c.owner_id AND u.organization_id = ?
        LEFT JOIN follow_counts fc ON fc.owner_id IS NOT DISTINCT FROM c.owner_id
        GROUP BY c.owner_id, u.name, fc.follow_count
        ORDER BY won_count DESC, lead_count DESC, u.name NULLS LAST
        LIMIT ? OFFSET ?
        """, (rs, rowNum) -> salesRow(rs), listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public List<TrendPointDTO> trend(SecurityUser actor, Filter raw, Granularity granularity) {
    Filter filter = normalize(actor, raw);
    Granularity unit = granularity == null ? Granularity.day : granularity;
    String sql = """
        WITH bounds AS (
          SELECT date_trunc(?::text, ?::timestamptz AT TIME ZONE 'UTC') AS first_bucket,
            date_trunc(?::text, (?::timestamptz - interval '1 microsecond') AT TIME ZONE 'UTC') AS last_bucket
        ), buckets AS (
          SELECT generate_series(first_bucket, last_bucket, ?::interval) AS bucket FROM bounds
        ), lead_counts AS (
          SELECT date_trunc(?::text, l.created_at AT TIME ZONE 'UTC') AS bucket, COUNT(*) AS count
          FROM leads l WHERE l.organization_id = ? AND l.archived_at IS NULL
            AND l.created_at >= ? AND l.created_at < ?
        """ + ownerClause(filter, "l") + """
          GROUP BY 1
        ), won_counts AS (
          SELECT date_trunc(?::text, l.closed_at AT TIME ZONE 'UTC') AS bucket, COUNT(*) AS count
          FROM leads l WHERE l.organization_id = ? AND l.archived_at IS NULL
            AND l.status = 'won'::lead_status AND l.closed_at IS NOT NULL
            AND l.closed_at >= ? AND l.closed_at < ?
        """ + ownerClause(filter, "l") + """
          GROUP BY 1
        )
        SELECT b.bucket AT TIME ZONE 'UTC' AS period_start,
          COALESCE(lc.count, 0) AS lead_count, COALESCE(wc.count, 0) AS won_count
        FROM buckets b
        LEFT JOIN lead_counts lc ON lc.bucket = b.bucket
        LEFT JOIN won_counts wc ON wc.bucket = b.bucket
        ORDER BY b.bucket
        LIMIT 366
        """;
    List<Object> args = new ArrayList<>();
    args.add(unit.sqlValue()); args.add(dbTime(filter.from())); args.add(unit.sqlValue()); args.add(dbTime(filter.to()));
    args.add(unit.interval()); args.add(unit.sqlValue());
    args.add(actor.organizationId()); args.add(dbTime(filter.from())); args.add(dbTime(filter.to()));
    addOwner(args, filter);
    args.add(unit.sqlValue()); args.add(actor.organizationId()); args.add(dbTime(filter.from())); args.add(dbTime(filter.to()));
    addOwner(args, filter);
    return jdbc.query(sql, (rs, rowNum) -> new TrendPointDTO(JdbcTimeUtils.fromDbTime(rs, "period_start"),
        rs.getLong("lead_count"), rs.getLong("won_count")), args.toArray());
  }

  public PageSupport.Result<LossReasonStatsDTO> lossReasons(SecurityUser actor, Filter raw, Integer page, Integer pageSize) {
    Filter filter = normalize(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    Object[] args = outcomeArgs(actor, filter);
    Long total = jdbc.queryForObject("""
        SELECT COUNT(DISTINCT COALESCE(NULLIF(BTRIM(lost_reason), ''), 'unspecified'))
        FROM leads WHERE organization_id = ? AND archived_at IS NULL
          AND status = 'lost'::lead_status AND closed_at IS NOT NULL AND closed_at >= ? AND closed_at < ?
        """ + ownerClause(filter, ""), Long.class, args);
    List<Object> listArgs = append(args, paging.pageSize(), paging.offset());
    List<LossReasonStatsDTO> items = jdbc.query("""
        SELECT COALESCE(NULLIF(BTRIM(lost_reason), ''), 'unspecified') AS reason, COUNT(*) AS count
        FROM leads WHERE organization_id = ? AND archived_at IS NULL
          AND status = 'lost'::lead_status AND closed_at IS NOT NULL AND closed_at >= ? AND closed_at < ?
        """ + ownerClause(filter, "") + " GROUP BY 1 ORDER BY count DESC, reason LIMIT ? OFFSET ?",
        (rs, rowNum) -> new LossReasonStatsDTO(rs.getString("reason"), rs.getLong("count")), listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public ResponseTimeStatsDTO responseTimes(SecurityUser actor, Filter raw) {
    Filter filter = normalize(actor, raw);
    ResponseAggregate aggregate = jdbc.queryForObject("""
        WITH first_follow_up AS (
          SELECT l.id, CASE WHEN MIN(f.occurred_at) IS NULL THEN NULL
            ELSE GREATEST(EXTRACT(EPOCH FROM (MIN(f.occurred_at) - l.created_at))::double precision,
              0.0::double precision) END AS seconds
          FROM leads l
          LEFT JOIN follow_ups f ON f.lead_id = l.id AND f.organization_id = l.organization_id AND f.deleted_at IS NULL
          WHERE l.organization_id = ? AND l.archived_at IS NULL AND l.created_at >= ? AND l.created_at < ?
        """ + ownerClause(filter, "l") + """
          GROUP BY l.id, l.created_at
        )
        SELECT COUNT(*) FILTER (WHERE seconds IS NOT NULL) AS responded,
          COUNT(*) FILTER (WHERE seconds IS NULL) AS unresponded,
          AVG(seconds) FILTER (WHERE seconds IS NOT NULL) AS avg_seconds,
          COUNT(*) FILTER (WHERE seconds < 3600) AS under_1h,
          COUNT(*) FILTER (WHERE seconds >= 3600 AND seconds < 86400) AS from_1h_to_24h,
          COUNT(*) FILTER (WHERE seconds >= 86400 AND seconds <= 604800) AS from_1d_to_7d,
          COUNT(*) FILTER (WHERE seconds > 604800) AS over_7d
        FROM first_follow_up
        """, (rs, rowNum) -> new ResponseAggregate(
            rs.getLong("responded"), rs.getLong("unresponded"), rs.getObject("avg_seconds", Double.class),
            rs.getLong("under_1h"), rs.getLong("from_1h_to_24h"), rs.getLong("from_1d_to_7d"), rs.getLong("over_7d")),
        leadArgs(actor, filter));
    List<ResponseTimeStatsDTO.Bucket> buckets = List.of(
        new ResponseTimeStatsDTO.Bucket("<1h", aggregate.under1h()),
        new ResponseTimeStatsDTO.Bucket("1h-24h", aggregate.from1hTo24h()),
        new ResponseTimeStatsDTO.Bucket("1d-7d", aggregate.from1dTo7d()),
        new ResponseTimeStatsDTO.Bucket(">7d", aggregate.over7d()),
        new ResponseTimeStatsDTO.Bucket("unresponded", aggregate.unresponded()));
    return new ResponseTimeStatsDTO(aggregate.responded(), aggregate.unresponded(), aggregate.avgSeconds(), buckets);
  }

  private ResponseSample responseSample(SecurityUser actor, Filter filter) {
    OverviewRow row = jdbc.queryForObject("""
        WITH first_follow_up AS (
          SELECT l.id, CASE WHEN MIN(f.occurred_at) IS NULL THEN NULL
            ELSE GREATEST(EXTRACT(EPOCH FROM (MIN(f.occurred_at) - l.created_at))::double precision,
              0.0::double precision) END AS seconds
          FROM leads l
          LEFT JOIN follow_ups f ON f.lead_id = l.id AND f.organization_id = l.organization_id AND f.deleted_at IS NULL
          WHERE l.organization_id = ? AND l.archived_at IS NULL AND l.created_at >= ? AND l.created_at < ?
        """ + ownerClause(filter, "l") + """
          GROUP BY l.id, l.created_at
        )
        SELECT COUNT(*) FILTER (WHERE seconds IS NOT NULL) AS responded,
          COUNT(*) FILTER (WHERE seconds IS NULL) AS unresponded,
          AVG(GREATEST(seconds, 0)) FILTER (WHERE seconds IS NOT NULL) AS avg_seconds
        FROM first_follow_up
        """, (rs, rowNum) -> new OverviewRow(rs.getLong("responded"), rs.getLong("unresponded"), 0,
            rs.getObject("avg_seconds", Double.class)), leadArgs(actor, filter));
    return new ResponseSample(row.total(), row.newCount(), row.avgCycle());
  }

  private Filter normalize(SecurityUser actor, Filter raw) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime from = raw == null ? null : raw.from();
    OffsetDateTime to = raw == null ? null : raw.to();
    if ((from == null) != (to == null)) {
      throw invalid("from 和 to 必须同时提供");
    }
    if (from == null) {
      to = now;
      from = now.minusDays(30);
    }
    from = from.withOffsetSameInstant(ZoneOffset.UTC);
    to = to.withOffsetSameInstant(ZoneOffset.UTC);
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
      throw invalid("统计范围无效，最大跨度为 366 天");
    }
    UUID ownerId = raw == null ? null : raw.ownerId();
    if (BusinessRules.isSales(actor)) {
      if (ownerId != null && !actor.id().equals(ownerId)) {
        throw new ApiException("FORBIDDEN", "销售只能查看自己的数据", HttpStatus.FORBIDDEN);
      }
      ownerId = actor.id();
    } else if (ownerId != null) {
      Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ?",
          Integer.class, ownerId, actor.organizationId());
      if (found == null || found != 1) {
        throw new ApiException("RESOURCE_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
      }
    }
    return new Filter(from, to, ownerId);
  }

  private Object[] leadArgs(SecurityUser actor, Filter filter) {
    List<Object> args = new ArrayList<>(List.of(actor.organizationId(), dbTime(filter.from()), dbTime(filter.to())));
    addOwner(args, filter);
    return args.toArray();
  }

  private Object[] outcomeArgs(SecurityUser actor, Filter filter) {
    return leadArgs(actor, filter);
  }

  private String ownerClause(Filter filter, String alias) {
    return filter.ownerId() == null ? "" : " AND " + (alias.isEmpty() ? "" : alias + ".") + "owner_id = ?";
  }

  private void addOwner(List<Object> args, Filter filter) {
    if (filter.ownerId() != null) args.add(filter.ownerId());
  }

  private OffsetDateTime dbTime(OffsetDateTime value) {
    return JdbcTimeUtils.toDbTime(value.toInstant());
  }

  private List<Object> append(Object[] initial, Object... rest) {
    List<Object> values = new ArrayList<>(java.util.Arrays.asList(initial));
    values.addAll(List.of(rest));
    return values;
  }

  private ChannelStatsDTO channel(ResultSet rs) throws SQLException {
    long leads = rs.getLong("lead_count");
    long wins = rs.getLong("won_count");
    return new ChannelStatsDTO(rs.getString("source"), leads, wins, leads == 0 ? 0 : (double) wins / leads);
  }

  private SalesRankingDTO salesRow(ResultSet rs) throws SQLException {
    return new SalesRankingDTO(rs.getObject("owner_id", UUID.class), rs.getString("owner_name"),
        rs.getLong("follow_up_count"), rs.getLong("won_count"), rs.getObject("avg_cycle", Double.class),
        rs.getLong("lead_count") == 0 ? 0 : rs.getLong("won_count") / (double) rs.getLong("lead_count"));
  }

  private ApiException invalid(String message) {
    return new ApiException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
  }

  public record Filter(OffsetDateTime from, OffsetDateTime to, UUID ownerId) {}
  private record OverviewRow(long total, long newCount, long won, Double avgCycle) {}
  private record ResponseSample(long responded, long unresponded, Double avgSeconds) {}
  private record ResponseAggregate(long responded, long unresponded, Double avgSeconds,
      long under1h, long from1hTo24h, long from1dTo7d, long over7d) {}
}

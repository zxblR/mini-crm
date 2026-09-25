import { LeadStatus } from '@mini-crm/shared';

export const LEAD_STATUS_LABELS: Record<LeadStatus, string> = {
  [LeadStatus.New]: '新建',
  [LeadStatus.Contacted]: '已联系',
  [LeadStatus.Qualified]: '需求确认',
  [LeadStatus.Proposal]: '报价/方案',
  [LeadStatus.Negotiation]: '谈判',
  [LeadStatus.Won]: '赢单',
  [LeadStatus.Lost]: '输单',
};

export const LEAD_STATUS_ORDER: LeadStatus[] = [
  LeadStatus.New,
  LeadStatus.Contacted,
  LeadStatus.Qualified,
  LeadStatus.Proposal,
  LeadStatus.Negotiation,
  LeadStatus.Won,
  LeadStatus.Lost,
];

export const OPEN_LEAD_STATUSES: LeadStatus[] = LEAD_STATUS_ORDER.filter(
  (status) => status !== LeadStatus.Won && status !== LeadStatus.Lost,
);

export const NEXT_SALES_STATUS: Partial<Record<LeadStatus, LeadStatus[]>> = {
  [LeadStatus.New]: [LeadStatus.Contacted],
  [LeadStatus.Contacted]: [LeadStatus.Qualified],
  [LeadStatus.Qualified]: [LeadStatus.Proposal],
  [LeadStatus.Proposal]: [LeadStatus.Negotiation],
  [LeadStatus.Negotiation]: [LeadStatus.Won, LeadStatus.Lost],
};

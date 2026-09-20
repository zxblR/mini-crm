import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { ActivityLogService } from '../common/logging/activity-log.service';
import type { AuthenticatedUser } from '../common/types/authenticated-user';

@Injectable()
export class TeamsService {
  constructor(private readonly prisma: PrismaService, private readonly activityLogService: ActivityLogService) {}

  async current(actor: AuthenticatedUser) {
    const team = await this.prisma.organization.findUnique({ where: { id: actor.organizationId } });
    if (!team) throw new NotFoundException('团队不存在');
    return { id: team.id, name: team.name, slug: team.slug, timezone: team.timezone };
  }

  async update(actor: AuthenticatedUser, data: { name?: string; timezone?: string }) {
    const team = await this.prisma.organization.update({ where: { id: actor.organizationId }, data });
    await this.activityLogService.record({ organizationId: actor.organizationId, actorId: actor.id, action: 'UPDATE_TEAM', resourceType: 'TEAM', resourceId: actor.organizationId });
    return { id: team.id, name: team.name, slug: team.slug, timezone: team.timezone };
  }
}

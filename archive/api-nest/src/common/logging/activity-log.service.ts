import { Injectable } from '@nestjs/common';
import type { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class ActivityLogService {
  constructor(private readonly prisma: PrismaService) {}

  async record(input: {
    organizationId: string;
    actorId?: string;
    action: string;
    resourceType: string;
    resourceId?: string;
    requestId?: string;
    metadata?: Record<string, unknown>;
  }): Promise<void> {
    await this.prisma.activityLog.create({
      data: { ...input, metadata: input.metadata as Prisma.InputJsonValue | undefined },
    });
  }
}

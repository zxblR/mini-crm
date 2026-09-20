import { Module } from '@nestjs/common';
import { ActivityLogService } from './logging/activity-log.service';
import { PrismaService } from '../prisma/prisma.service';

@Module({ providers: [PrismaService, ActivityLogService], exports: [PrismaService, ActivityLogService] })
export class CommonModule {}

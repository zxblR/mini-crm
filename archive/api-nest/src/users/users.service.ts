import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import * as argon2 from 'argon2';
import type { RoleCode as PrismaRoleCode } from '@prisma/client';
import type { RoleCode, SessionUser, UpdateProfileInput, UserSummary } from '@mini-crm/shared';
import { PrismaService } from '../prisma/prisma.service';
import { ActivityLogService } from '../common/logging/activity-log.service';
import type { AuthenticatedUser } from '../common/types/authenticated-user';
import type { CreateUserDto } from './dto/create-user.dto';
import type { UpdateProfileDto } from './dto/update-profile.dto';
import type { UpdateUserDto } from './dto/update-user.dto';

@Injectable()
export class UsersService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly activityLogService: ActivityLogService,
  ) {}

  toSessionUser(user: { id: string; organizationId: string; name: string; email: string | null; phone: string | null; roles: Array<{ code: PrismaRoleCode }> }): SessionUser {
    return {
      id: user.id,
      organizationId: user.organizationId,
      name: user.name,
      email: user.email,
      phone: user.phone,
      roles: user.roles.map((role) => role.code as unknown as RoleCode),
      permissions: [],
    };
  }

  async getAuthenticatedUser(id: string): Promise<AuthenticatedUser> {
    const user = await this.prisma.user.findUnique({ where: { id }, include: { roles: true } });
    if (!user || !user.isActive) throw new NotFoundException('用户不存在或已停用');
    return this.toSessionUser(user);
  }

  private summary(user: {
    id: string;
    organizationId: string;
    name: string;
    email: string | null;
    phone: string | null;
    isActive: boolean;
    lastLoginAt: Date | null;
    createdAt: Date;
    roles: Array<{ code: PrismaRoleCode }>;
  }): UserSummary {
    return {
      id: user.id,
      organizationId: user.organizationId,
      name: user.name,
      email: user.email,
      phone: user.phone,
      roleCodes: user.roles.map((role) => role.code as unknown as RoleCode),
      isActive: user.isActive,
      lastLoginAt: user.lastLoginAt?.toISOString() ?? null,
      createdAt: user.createdAt.toISOString(),
    };
  }

  async updateMe(actor: AuthenticatedUser, dto: UpdateProfileDto): Promise<UserSummary> {
    return this.updateProfile(actor, dto);
  }

  async updateProfile(actor: AuthenticatedUser, dto: UpdateProfileInput): Promise<UserSummary> {
    const user = await this.prisma.user.update({
      where: { id: actor.id },
      data: {
        name: dto.name,
        email: dto.email,
        normalizedEmail: dto.email?.trim().toLowerCase(),
        phone: dto.phone,
        normalizedPhone: dto.phone?.trim(),
      },
      include: { roles: true },
    });
    await this.activityLogService.record({ organizationId: actor.organizationId, actorId: actor.id, action: 'UPDATE_USER', resourceType: 'USER', resourceId: actor.id });
    return this.summary(user);
  }

  async list(actor: AuthenticatedUser): Promise<UserSummary[]> {
    const users = await this.prisma.user.findMany({ where: { organizationId: actor.organizationId }, orderBy: { createdAt: 'asc' }, include: { roles: true } });
    return users.map((user) => this.summary(user));
  }

  async create(actor: AuthenticatedUser, dto: CreateUserDto): Promise<UserSummary> {
    if (!dto.email && !dto.phone) throw new ConflictException('邮箱或手机号至少填写一个');
    const email = dto.email?.trim().toLowerCase();
    const phone = dto.phone?.trim();
    const user = await this.prisma.user.create({
      data: {
        organizationId: actor.organizationId,
        name: dto.name.trim(),
        email,
        normalizedEmail: email,
        phone,
        normalizedPhone: phone,
        passwordHash: await argon2.hash(dto.password),
        roles: { create: dto.roleCodes.map((code) => ({ code: code as unknown as PrismaRoleCode })) },
      },
      include: { roles: true },
    });
    await this.activityLogService.record({ organizationId: actor.organizationId, actorId: actor.id, action: 'CREATE_USER', resourceType: 'USER', resourceId: user.id });
    return this.summary(user);
  }

  async update(actor: AuthenticatedUser, id: string, dto: UpdateUserDto): Promise<UserSummary> {
    const user = await this.prisma.user.update({
      where: { id },
      data: { name: dto.name, email: dto.email, normalizedEmail: dto.email?.toLowerCase(), phone: dto.phone },
      include: { roles: true },
    });
    if (dto.roleCodes) {
      await this.prisma.userRole.deleteMany({ where: { userId: id } });
      await this.prisma.userRole.createMany({ data: dto.roleCodes.map((code) => ({ userId: id, code: code as unknown as PrismaRoleCode })) });
    }
    await this.activityLogService.record({ organizationId: actor.organizationId, actorId: actor.id, action: 'UPDATE_USER', resourceType: 'USER', resourceId: id });
    return this.summary({
      ...user,
      roles: dto.roleCodes?.map((code) => ({ code: code as unknown as PrismaRoleCode })) ?? user.roles,
    });
  }

  async updateStatus(actor: AuthenticatedUser, id: string, isActive: boolean): Promise<UserSummary> {
    const user = await this.prisma.user.update({ where: { id }, data: { isActive }, include: { roles: true } });
    await this.activityLogService.record({ organizationId: actor.organizationId, actorId: actor.id, action: 'UPDATE_USER_STATUS', resourceType: 'USER', resourceId: id, metadata: { isActive } });
    return this.summary(user);
  }
}

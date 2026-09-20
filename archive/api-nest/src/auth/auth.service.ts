import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import * as argon2 from 'argon2';
import type { LoginResult } from '@mini-crm/shared';
import { PrismaService } from '../prisma/prisma.service';
import { UsersService } from '../users/users.service';
import { ActivityLogService } from '../common/logging/activity-log.service';
import type { LoginDto } from './dto/login.dto';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwtService: JwtService,
    private readonly usersService: UsersService,
    private readonly activityLogService: ActivityLogService,
  ) {}

  async login(dto: LoginDto): Promise<LoginResult> {
    const account = dto.account.trim().toLowerCase();
    const user = await this.prisma.user.findFirst({
      where: { OR: [{ normalizedEmail: account }, { normalizedPhone: account }] },
      include: { roles: true },
    });
    if (!user || !user.isActive || !(await argon2.verify(user.passwordHash, dto.password))) {
      throw new UnauthorizedException('账号或密码错误');
    }

    await this.prisma.user.update({ where: { id: user.id }, data: { lastLoginAt: new Date() } });
    await this.activityLogService.record({
      organizationId: user.organizationId,
      actorId: user.id,
      action: 'LOGIN',
      resourceType: 'USER',
      resourceId: user.id,
    });
    const accessToken = await this.jwtService.signAsync(
      { sub: user.id, organizationId: user.organizationId, type: 'access' },
      { expiresIn: '15m' },
    );
    return { accessToken, expiresIn: 900, user: this.usersService.toSessionUser(user) };
  }
}

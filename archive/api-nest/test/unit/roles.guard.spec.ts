/* global describe, expect, it, jest */
import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { RoleCode } from '@mini-crm/shared';
import { RolesGuard } from '../../src/common/guards/roles.guard';

describe('RolesGuard', () => {
  it('allows a user with a declared role', () => {
    const reflector = new Reflector();
    const guard = new RolesGuard(reflector);
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue([RoleCode.Admin]);
    const context = {
      getHandler: jest.fn(),
      getClass: jest.fn(),
      switchToHttp: () => ({ getRequest: () => ({ user: { roles: [RoleCode.Admin] } }) }),
    } as unknown as ExecutionContext;

    expect(guard.canActivate(context)).toBe(true);
  });

  it('rejects a user without a declared role', () => {
    const reflector = new Reflector();
    const guard = new RolesGuard(reflector);
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue([RoleCode.Owner]);
    const context = {
      getHandler: jest.fn(),
      getClass: jest.fn(),
      switchToHttp: () => ({ getRequest: () => ({ user: { roles: [RoleCode.Sales] } }) }),
    } as unknown as ExecutionContext;

    expect(() => guard.canActivate(context)).toThrow('当前用户没有执行此操作的权限');
  });
});

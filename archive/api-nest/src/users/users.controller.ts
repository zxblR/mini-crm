import { Body, Controller, Get, Param, Patch, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { RoleCode } from '@mini-crm/shared';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import type { AuthenticatedUser } from '../common/types/authenticated-user';
import { CreateUserDto } from './dto/create-user.dto';
import { UpdateProfileDto } from './dto/update-profile.dto';
import { UpdateStatusDto } from './dto/update-status.dto';
import { UsersService } from './users.service';

@ApiTags('users')
@ApiBearerAuth()
@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get('me')
  me(@CurrentUser() actor: AuthenticatedUser) {
    return actor;
  }

  @Patch('me')
  updateMe(@CurrentUser() actor: AuthenticatedUser, @Body() dto: UpdateProfileDto) {
    return this.usersService.updateMe(actor, dto);
  }

  @Roles(RoleCode.Owner, RoleCode.Admin)
  @Get()
  list(@CurrentUser() actor: AuthenticatedUser) {
    return this.usersService.list(actor);
  }

  @Roles(RoleCode.Owner, RoleCode.Admin)
  @Post()
  create(@CurrentUser() actor: AuthenticatedUser, @Body() dto: CreateUserDto) {
    return this.usersService.create(actor, dto);
  }

  @Roles(RoleCode.Owner, RoleCode.Admin)
  @Patch(':id/status')
  updateStatus(@CurrentUser() actor: AuthenticatedUser, @Param('id') id: string, @Body() dto: UpdateStatusDto) {
    return this.usersService.updateStatus(actor, id, dto.isActive);
  }
}

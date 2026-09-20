import { Body, Controller, Get, Patch } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { RoleCode } from '@mini-crm/shared';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import type { AuthenticatedUser } from '../common/types/authenticated-user';
import { UpdateTeamDto } from './dto/update-team.dto';
import { TeamsService } from './teams.service';

@ApiTags('teams')
@ApiBearerAuth()
@Controller('teams/current')
export class TeamsController {
  constructor(private readonly teamsService: TeamsService) {}

  @Get()
  current(@CurrentUser() actor: AuthenticatedUser) {
    return this.teamsService.current(actor);
  }

  @Roles(RoleCode.Owner)
  @Patch()
  update(@CurrentUser() actor: AuthenticatedUser, @Body() dto: UpdateTeamDto) {
    return this.teamsService.update(actor, dto);
  }
}

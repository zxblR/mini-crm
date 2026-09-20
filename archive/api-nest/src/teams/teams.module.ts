import { Module } from '@nestjs/common';
import { CommonModule } from '../common/common.module';
import { TeamsController } from './teams.controller';
import { TeamsService } from './teams.service';

@Module({ imports: [CommonModule], controllers: [TeamsController], providers: [TeamsService] })
export class TeamsModule {}

import { Controller, Get } from '@nestjs/common';

import type { ApiHealthResponse } from '@mini-crm/shared';
import { Public } from './common/decorators/public.decorator';

@Controller('health')
export class HealthController {
  @Public()
  @Get()
  getHealth(): ApiHealthResponse {
    return {
      status: 'ok',
      service: 'api',
    };
  }
}

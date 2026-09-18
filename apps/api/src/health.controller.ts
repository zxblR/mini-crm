import { Controller, Get } from '@nestjs/common';

import type { ApiHealthResponse } from '@mini-crm/shared';

@Controller('health')
export class HealthController {
  @Get()
  getHealth(): ApiHealthResponse {
    return {
      status: 'ok',
      service: 'api',
    };
  }
}

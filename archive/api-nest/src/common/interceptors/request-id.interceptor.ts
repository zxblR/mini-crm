import { randomUUID } from 'node:crypto';
import { CallHandler, ExecutionContext, Injectable, NestInterceptor } from '@nestjs/common';
import type { Response } from 'express';
import { Observable } from 'rxjs';

@Injectable()
export class RequestIdInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<{ requestId?: string }>();
    const response = context.switchToHttp().getResponse<Response>();
    const requestId = randomUUID();
    request.requestId = requestId;
    response.setHeader('X-Request-Id', requestId);
    return next.handle();
  }
}

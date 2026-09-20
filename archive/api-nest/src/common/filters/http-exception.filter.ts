import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus } from '@nestjs/common';
import type { Response } from 'express';

@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();
    const request = host.switchToHttp().getRequest<{ requestId?: string }>();
    const status = exception instanceof HttpException ? exception.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    const exceptionResponse = exception instanceof HttpException ? exception.getResponse() : undefined;
    const message = typeof exceptionResponse === 'string' ? exceptionResponse : '服务内部错误';
    const details =
      typeof exceptionResponse === 'object' && exceptionResponse !== null && 'message' in exceptionResponse
        ? { validation: (exceptionResponse as { message?: unknown }).message }
        : {};
    response.status(status).json({
      data: null,
      meta: { requestId: request.requestId ?? '' },
      error: { code: status === 401 ? 'UNAUTHORIZED' : status === 403 ? 'FORBIDDEN' : status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED', message, details },
    });
  }
}

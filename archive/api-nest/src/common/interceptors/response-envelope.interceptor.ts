import { CallHandler, ExecutionContext, Injectable, NestInterceptor } from '@nestjs/common';
import { map, Observable } from 'rxjs';

@Injectable()
export class ResponseEnvelopeInterceptor<T> implements NestInterceptor<T, { data: T; meta: { requestId: string }; error: null }> {
  intercept(context: ExecutionContext, next: CallHandler<T>): Observable<{ data: T; meta: { requestId: string }; error: null }> {
    const request = context.switchToHttp().getRequest<{ requestId?: string }>();
    return next.handle().pipe(map((data) => ({ data, meta: { requestId: request.requestId ?? '' }, error: null })));
  }
}

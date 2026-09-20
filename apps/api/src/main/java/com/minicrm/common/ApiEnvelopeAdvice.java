package com.minicrm.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiEnvelopeAdvice implements ResponseBodyAdvice<ApiEnvelope<?>> {
  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return ApiEnvelope.class.isAssignableFrom(returnType.getParameterType())
        || ResponseEntity.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public ApiEnvelope<?> beforeBodyWrite(
      ApiEnvelope<?> body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest serverRequest,
      ServerHttpResponse serverResponse) {
    if (body == null || body.meta() == null || body.meta().requestId() != null) {
      return body;
    }
    Object value = requestId();
    return body.withRequestId(value == null ? "" : value.toString());
  }

  private Object requestId() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    return attributes.getRequest().getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

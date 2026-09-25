package com.dking.mini_calling.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // 类或方法标了 @IgnoreWrap → 跳过
        if (returnType.hasMethodAnnotation(IgnoreWrap.class)
                || returnType.getDeclaringClass().isAnnotationPresent(IgnoreWrap.class)) {
            return false;
        }
        // 返回类型本身就是 Result（如异常处理器）→ 跳过，避免二次包装
        return !Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        // 排除监控/文档路径，避免污染原生结构
        String path = ((ServletServerHttpRequest) request).getServletRequest().getRequestURI();
        if (path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")) {
            return body;
        }

        // 已经是 Result（比如异常处理器返回的）→ 放行
        if (body instanceof Result) {
            return body;
        }

        // 关键坑：String 走 StringHttpMessageConverter，不能直接返回 Result 对象
        // 必须手动序列化成 JSON 字符串，否则抛 ClassCastException
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(Result.success(body));
            } catch (Exception e) {
                throw new RuntimeException("String 响应序列化失败", e);
            }
        }

        return Result.success(body);
    }
}

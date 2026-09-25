package com.dking.mini_calling.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /** 统一使用这个名字引用 scheme */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Calling API")
                        .description("Spring Boot 3 + Spring Security 6 + JWT + MyBatis-Plus 接口文档")
                        .version("v1.0.0"))
                // 声明全局安全要求：所有接口默认要求 Bearer Token
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")   // 会让 Swagger UI 自动在请求头拼接 Bearer xxx
                                        .bearerFormat("JWT")
                                        .description("登录接口返回的 token 直接粘贴，无需手动加 Bearer 前缀")));
    }
}

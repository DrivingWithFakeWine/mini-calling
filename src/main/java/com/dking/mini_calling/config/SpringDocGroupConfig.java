package com.dking.mini_calling.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/*
配置后 Swagger UI 右上角会出现下拉框切换分组，
/v3/api-docs/01-认证 这类地址也可以被 Apifox 按组导入
 */
@Configuration
public class SpringDocGroupConfig {

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("01-认证")
                .pathsToMatch("/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("02-用户管理")
                .pathsToMatch("/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi roleApi() {
        return GroupedOpenApi.builder()
                .group("03-角色管理")
                .pathsToMatch("/roles/**")
                .build();
    }

    @Bean
    public GroupedOpenApi permissionApi() {
        return GroupedOpenApi.builder()
                .group("04-权限管理")
                .pathsToMatch("/permissions/**")
                .build();
    }


    // 兜底组，防止忘记添加分组，导致UI界面没渲染
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("00-全部接口")
                .pathsToMatch("/**")
                .build();
    }

}

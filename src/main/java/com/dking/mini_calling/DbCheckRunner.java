package com.dking.mini_calling;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DbCheckRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        Long userCount  = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        Long roleCount  = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role", Long.class);
        Long userRoleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user_role", Long.class);

        System.out.println("========== 数据库自检 ==========");
        System.out.println("MySQL 版本      : " + version);
        System.out.println("sys_user 记录数 : " + userCount + "（应为 1）");
        System.out.println("sys_role 记录数 : " + roleCount + "（应为 1）");
        System.out.println("关联关系记录数  : " + userRoleCount + "（应为 1）");
        System.out.println("================================");
    }
}

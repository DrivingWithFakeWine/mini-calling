package com.dking.mini_calling;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Transactional   // ← 关键：每个用例结束自动回滚
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mini_calling_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("sql/init_test_schema.sql");

    static {   // ← 类加载时启动，不写 @Container，不 stop
        mysql.start();
    }
}


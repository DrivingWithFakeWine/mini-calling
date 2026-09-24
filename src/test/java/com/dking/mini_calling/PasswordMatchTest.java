package com.dking.mini_calling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class PasswordMatchTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void checkSeedPassword() {
        boolean ok = passwordEncoder.matches("admin123",
                "$2a$10$X5wFBtLrL/kUCqFb0CAbU.FSMmEBQ0TZQrVvz3FvGz/UoTkkgA/9a");
        System.out.println("admin123 与密文是否匹配: " + ok);
    }

    @Test
    void printNewHash() {
        String hash = passwordEncoder.encode("admin123");
        System.out.println("新密文: " + hash);
    }
}

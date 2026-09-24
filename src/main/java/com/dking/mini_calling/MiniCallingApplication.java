package com.dking.mini_calling;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dking.mini_calling.mapper")
public class MiniCallingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniCallingApplication.class, args);
    }


}

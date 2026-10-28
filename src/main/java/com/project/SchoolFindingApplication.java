package com.project;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.project.mapper")
public class SchoolFindingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolFindingApplication.class, args);
        System.out.println("""
                ====================================================
                School-Finding 启动成功
                Swagger : http://localhost:8080/doc.html
                ====================================================
                """);
    }
}

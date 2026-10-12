package com.boticompara.usuario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendUsuarioApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendUsuarioApplication.class, args);
    }
}
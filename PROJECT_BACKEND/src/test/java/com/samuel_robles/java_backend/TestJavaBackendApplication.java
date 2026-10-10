package com.samuel_robles.java_backend;

import org.springframework.boot.SpringApplication;

public class TestJavaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(JavaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

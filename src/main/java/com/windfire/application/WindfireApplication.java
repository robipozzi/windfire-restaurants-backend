package com.windfire.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.windfire.service", "com.windfire.application", "com.ibm.javametrics.spring"})
public class WindfireApplication {

    public static void main(String[] args) {
        SpringApplication.run(WindfireApplication.class, args);
    }
}
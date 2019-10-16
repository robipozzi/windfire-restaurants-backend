package com.rp.windfire.application;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rp.windfire", "com.ibm.javametrics.spring"})
public class WindfireApplication {
	private static final Logger logger = LoggerFactory.getLogger(WindfireApplication.class);

    public static void main(String[] args) {
    	ApplicationContext ctx = SpringApplication.run(WindfireApplication.class, args);
    	logger.info("Application " + ctx.getApplicationName() + " started");
    }
    
    @Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			logger.debug("Let's inspect the beans provided by Spring Boot:");
			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);
			for (String beanName : beanNames) {
				logger.debug(beanName);
			}
		};
    }
}
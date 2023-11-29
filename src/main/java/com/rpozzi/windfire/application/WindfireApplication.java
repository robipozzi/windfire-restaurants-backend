package com.rpozzi.windfire.application;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "com.rpozzi.windfire" })
public class WindfireApplication {
	private static final Logger logger = LoggerFactory.getLogger(WindfireApplication.class);
	@Value(value = "${environment}")
	private String environment;

	public static void main(String[] args) {
		SpringApplication.run(WindfireApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			logger.debug("Let's inspect the beans provided by Spring Boot:");
			logger.debug("************** Spring Boot beans - START **************");
			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);
			for (String beanName : beanNames) {
				logger.debug(beanName);
			}
			logger.debug("************** Spring Boot beans - END **************");
			
			logger.debug("Print application configuration parameters");
			logger.debug("************** Application configuration parameters - START **************");
			logger.debug("Environment :  " + environment);
			logger.debug("************** Application configuration parameters - END **************");
			
			logger.info("Application " + ctx.getId() + " started !!!");
		};
	}
}
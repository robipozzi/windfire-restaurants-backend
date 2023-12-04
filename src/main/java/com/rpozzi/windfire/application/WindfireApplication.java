package com.rpozzi.windfire.application;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.rpozzi.windfire")
@ComponentScan(basePackages = "com.rpozzi.windfire")
public class WindfireApplication {
	private static final Logger logger = LoggerFactory.getLogger(WindfireApplication.class);
	@Autowired
	private Environment environment;

	public static void main(String[] args) {
		SpringApplication.run(WindfireApplication.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			printSpringBootBeans(ctx);
			printApplicationConfigurationProperties();
			logger.info("Application " + ctx.getId() + " started !!!");
		};
	}

	private void printSpringBootBeans(ApplicationContext ctx) {
		logger.debug("Let's inspect the beans provided by Spring Boot:");
		logger.debug("************** Spring Boot beans - START **************");
		String[] beanNames = ctx.getBeanDefinitionNames();
		Arrays.sort(beanNames);
		for (String beanName : beanNames) {
			logger.debug(beanName);
		}
		logger.debug("************** Spring Boot beans - END **************");
	}

	private void printApplicationConfigurationProperties() {
		logger.debug("============== Application properties - START ==============");
		String serverPort = environment.getProperty(ApplicationProperties.SERVER_PORT);
		logger.info("Server Port = " + serverPort);
		logger.info("The following endpoints are available by default :");
		logger.info("  Check   : http://localhost:" + serverPort + "/check");
		logger.debug("============== Application properties - END ==============");
	}
}
package com.rpozzi.windfire.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ApplicationConfiguration implements IApplicationConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(ApplicationConfiguration.class);
	@Value("${spring.application.name}")
	private String applicationName;
	@Value("${environment}")
	private String environment;
	
	public String check() {
		logger.info("Environment = " + environment);
		return "Dependency injection and configuration reading for application " + applicationName + " work !!!";
	}

	public String getApplicationName() {
		return applicationName;
	}

	public String getEnvironment() {
		return environment;
	}
}
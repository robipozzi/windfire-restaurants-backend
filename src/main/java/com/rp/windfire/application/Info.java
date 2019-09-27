package com.rp.windfire.application;

import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
public class Info {
	private static final Logger logger = LoggerFactory.getLogger(Info.class);
	@Autowired
	Environment environment;
	
	@EventListener(ApplicationReadyEvent.class)
    public void contextRefreshedEvent() {
		String port = environment.getProperty("server.port");
		String context = environment.getProperty("server.context-path");
		String env = environment.getProperty("environment");
		logger.info("Port from environment = " + port);
		logger.info("Context from environment = " + context);
		logger.info("Environment from environment = " + env);
		logger.info("The following endpoints are available by default :");
		logger.info("  Health  : http://localhost:" + port + context + "/health");
		logger.info("  Check   : http://localhost:" + port + context + "/v1/check");
    }

}
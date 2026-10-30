package com.rpozzi.windfire.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The Raspberry deployment runs with no Spring profile, so the defaults must keep Swagger off;
 * only the dev profile turns it on.
 */
class SwaggerProfileTest {

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = SwaggerProfileTest.class.getResourceAsStream("/" + resource)) {
			properties.load(in);
		}
		return properties;
	}

	@Test
	void swaggerIsOffWithoutProfile() throws IOException {
		Properties defaults = load("application.properties");
		assertEquals("false", defaults.getProperty("springdoc.api-docs.enabled"));
		assertEquals("false", defaults.getProperty("springdoc.swagger-ui.enabled"));
		assertEquals("false", defaults.getProperty("windfire.swagger.public"));
	}

	@Test
	void swaggerIsOnWithDevProfile() throws IOException {
		Properties dev = load("application-dev.properties");
		assertEquals("true", dev.getProperty("springdoc.api-docs.enabled"));
		assertEquals("true", dev.getProperty("springdoc.swagger-ui.enabled"));
		assertEquals("true", dev.getProperty("windfire.swagger.public"));
	}

}

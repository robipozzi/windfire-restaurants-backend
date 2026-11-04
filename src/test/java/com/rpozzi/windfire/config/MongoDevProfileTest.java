package com.rpozzi.windfire.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import com.rpozzi.windfire.application.WindfireApplication;
import com.rpozzi.windfire.restaurants.entities.IRestaurantRepository;
import com.rpozzi.windfire.testsupport.Does;

/**
 * The dev profile points the backend at the dev MongoDB cluster, never at prod, and never overrides the test URI.
 */
@SpringBootTest(classes = WindfireApplication.class)
@ActiveProfiles({ "test", "dev" })
@DisplayName("Application · MongoDB with the dev profile")
class MongoDevProfileTest {

	private static final String PROD_HOST = "windfire.rxdd4.mongodb.net";

	@Autowired
	private Environment environment;
	@MockBean
	private JwtDecoder jwtDecoder;
	@MockBean
	private IRestaurantRepository repository;

	@Test
	@DisplayName("Dev profile selects the dev cluster and user")
	@Does("windfire.mongodb.host = windfire-dev.dx6zesz.mongodb.net (not the prod host), windfire.mongodb.user = windfire-dev-app")
	void devProfileSelectsDevCluster() {
		assertThat(environment.getProperty("windfire.mongodb.host"))
				.isEqualTo("windfire-dev.dx6zesz.mongodb.net")
				.isNotEqualTo(PROD_HOST);
		assertThat(environment.getProperty("windfire.mongodb.user")).isEqualTo("windfire-dev-app");
	}

	@Test
	@DisplayName("Tests never get an Atlas connection string")
	@Does("with profiles {test, dev}, spring.data.mongodb.uri is still the localhost URI of application-test.properties")
	void testUriIsNotOverriddenByDev() {
		assertThat(environment.getProperty("spring.data.mongodb.uri")).isEqualTo("mongodb://localhost:27017/restaurants");
	}

}

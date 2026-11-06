package com.rpozzi.windfire.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import com.mongodb.MongoClientSettings;
import com.rpozzi.windfire.application.WindfireApplication;
import com.rpozzi.windfire.restaurants.entities.IRestaurantRepository;
import com.rpozzi.windfire.testsupport.Does;

/**
 * MongoDB Atlas is signed by a public CA: the Mongo client must keep the JVM default trust and never pick up the
 * "keycloak" SSL bundle, which only trusts the Windfire Keycloak Root CA.
 */
@SpringBootTest(classes = WindfireApplication.class)
@ActiveProfiles("test")
@DisplayName("Application · MongoDB TLS trust")
class MongoTlsTrustTest {

	@Autowired
	private Environment environment;
	@Autowired
	private List<MongoClientSettingsBuilderCustomizer> customizers;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private IRestaurantRepository repository;
	// The keycloak bundle file is not present in tests (HOME_DIR unset); Mongo must not need any bundle anyway.
	// If spring.mongodb.ssl.bundle were ever set, getBundle() returns null and this test fails.
	@MockitoBean
	private SslBundles sslBundles;

	@Test
	@DisplayName("Mongo client has no custom SSLContext")
	@Does("Customizers applied as Spring Boot does → sslSettings.context is null, so the driver uses SSLContext.getDefault() (JDK cacerts)")
	void mongoUsesJvmDefaultTrust() {
		MongoClientSettings.Builder builder = MongoClientSettings.builder();
		customizers.forEach(customizer -> customizer.customize(builder));

		assertThat(builder.build().getSslSettings().getContext()).isNull();
	}

	@Test
	@DisplayName("No SSL bundle is wired into MongoDB")
	@Does("spring.mongodb.ssl.bundle is not set (in particular, never the keycloak bundle)")
	void noBundleForMongo() {
		assertThat(environment.getProperty("spring.mongodb.ssl.bundle")).isNull();
	}

}

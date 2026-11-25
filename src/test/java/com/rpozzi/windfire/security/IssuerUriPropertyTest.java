package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import com.rpozzi.windfire.testsupport.Does;

/**
 * application.properties builds the expected issuer from KEYCLOAK_ISSUER_URL (a base URL, like the auth server's),
 * falling back to the LAN address, while the JWKS stays on the LAN.
 */
@DisplayName("Security · issuer-uri property")
class IssuerUriPropertyTest {

	private static final String ISSUER_URI = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
	private static final String JWK_SET_URI = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

	private static StandardEnvironment environment(Map<String, Object> env) throws Exception {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("env", env));
		environment.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
		return environment;
	}

	@Test
	@Does("KEYCLOAK_ISSUER_URL=https://auth.windfire.cc → issuer https://auth.windfire.cc/realms/windfire, JWKS still on raspberry01")
	@DisplayName("Public issuer base URL gets the realm appended")
	void publicIssuer() throws Exception {
		StandardEnvironment env = environment(Map.of("KEYCLOAK_SERVER", "raspberry01", "KEYCLOAK_SERVER_PORT", "8443",
				"KEYCLOAK_ISSUER_URL", "https://auth.windfire.cc"));
		assertEquals("https://auth.windfire.cc/realms/windfire", env.getProperty(ISSUER_URI));
		assertEquals("https://raspberry01:8443/realms/windfire/protocol/openid-connect/certs", env.getProperty(JWK_SET_URI));
	}

	@Test
	@Does("No KEYCLOAK_ISSUER_URL → issuer https://raspberry01:8443/realms/windfire")
	@DisplayName("Without KEYCLOAK_ISSUER_URL the issuer is the LAN one")
	void lanIssuerByDefault() throws Exception {
		StandardEnvironment env = environment(Map.of("KEYCLOAK_SERVER", "raspberry01", "KEYCLOAK_SERVER_PORT", "8443"));
		assertEquals("https://raspberry01:8443/realms/windfire", env.getProperty(ISSUER_URI));
	}
}

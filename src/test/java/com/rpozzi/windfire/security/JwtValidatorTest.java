package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import com.rpozzi.windfire.testsupport.Does;

/**
 * SecurityConfig.jwtValidator: default issuer and timestamp checks plus the API audience, without Spring.
 */
@DisplayName("Security · JWT validator")
class JwtValidatorTest {

	private static final String ISSUER = "https://localhost/realms/test";
	private static final String API_CLIENT_ID = "windfire-restaurants-api";
	private static final OAuth2TokenValidator<Jwt> VALIDATOR = SecurityConfig.jwtValidator(ISSUER, API_CLIENT_ID);

	/** A valid token; the customizer breaks one aspect of it. */
	private static boolean isValid(Consumer<Jwt.Builder> customizer) {
		Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none")
				.issuer(ISSUER)
				.subject("user")
				.audience(List.of("account", API_CLIENT_ID))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300));
		customizer.accept(builder);
		return !VALIDATOR.validate(builder.build()).hasErrors();
	}

	@Test
	@DisplayName("Valid token passes")
	@Does("Right issuer, aud contains the API client, not expired → no validation errors")
	void validTokenPasses() {
		assertTrue(isValid(b -> { }));
	}

	@Test
	@DisplayName("Missing audience fails")
	@Does("Token without aud → validation error")
	void missingAudienceFails() {
		assertFalse(isValid(b -> b.claims(c -> c.remove("aud"))));
	}

	@Test
	@DisplayName("Audience without the API client fails")
	@Does("aud = [account] → validation error")
	void wrongAudienceFails() {
		assertFalse(isValid(b -> b.audience(List.of("account"))));
	}

	@Test
	@DisplayName("Wrong issuer fails")
	@Does("iss = https://localhost/realms/other → validation error")
	void wrongIssuerFails() {
		assertFalse(isValid(b -> b.issuer("https://localhost/realms/other")));
	}

	@Test
	@DisplayName("Expired token fails, within clock skew passes")
	@Does("exp 2 minutes ago → error; exp 30 s ago (inside the 60 s skew) → still valid")
	void expiredTokenFails() {
		assertFalse(isValid(b -> b.issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(120))));
		assertTrue(isValid(b -> b.issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(30))));
	}

	@Test
	@DisplayName("Token not yet valid fails")
	@Does("nbf 5 minutes in the future → validation error")
	void notBeforeInFutureFails() {
		assertFalse(isValid(b -> b.notBefore(Instant.now().plusSeconds(300))));
	}

}

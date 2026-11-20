package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestOperations;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.rpozzi.windfire.testsupport.Does;

/**
 * The production decoder built by SecurityConfig: RS256 keys from the LAN JWKS endpoint, then the issuer and audience
 * checks together. Keycloak's JWKS endpoint is a mocked RestOperations, so nothing is contacted.
 */
@DisplayName("Security · JWT decoder")
class JwtDecoderTest {

	private static final String ISSUER = "https://raspberry01:8443/realms/windfire";
	private static final String JWK_SET_URI = ISSUER + "/protocol/openid-connect/certs";
	private static final String API_CLIENT_ID = "windfire-restaurants-api";
	private static final String KEY_ID = "test-key";

	private static RSAKey rsaKey;
	private RestOperations keycloak;
	private JwtDecoder decoder;

	@BeforeEach
	void setUp() throws Exception {
		if (rsaKey == null) {
			rsaKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
		}
		keycloak = mock(RestOperations.class);
		when(keycloak.exchange(any(RequestEntity.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(new JWKSet(rsaKey.toPublicJWK()).toString()));
		decoder = SecurityConfig.jwtDecoder(JWK_SET_URI, keycloak, ISSUER, API_CLIENT_ID);
	}

	/** A valid token signed with the JWKS key; the customizer breaks one aspect of it. */
	private static String token(Consumer<JwtClaimsSet.Builder> customizer) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject("user")
				.audience(List.of("account", API_CLIENT_ID))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300));
		customizer.accept(claims);
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)))
				.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build(), claims.build()))
				.getTokenValue();
	}

	@Test
	@DisplayName("Valid token decodes, keys come only from the LAN JWKS")
	@Does("Right issuer and audience → decoded; the only Keycloak call is the JWKS URI (no OIDC discovery)")
	@SuppressWarnings("rawtypes")
	void validTokenDecodesWithLanJwksOnly() {
		assertEquals("user", decoder.decode(token(c -> { })).getSubject());

		ArgumentCaptor<RequestEntity> request = ArgumentCaptor.forClass(RequestEntity.class);
		verify(keycloak, atLeastOnce()).exchange(request.capture(), eq(String.class));
		request.getAllValues().forEach(r -> assertEquals(URI.create(JWK_SET_URI), r.getUrl()));
	}

	@Test
	@DisplayName("Wrong issuer is rejected")
	@Does("Validly signed token with iss = https://app.example.com/realms/windfire → JwtValidationException on iss")
	void wrongIssuerIsRejected() {
		JwtValidationException e = assertThrows(JwtValidationException.class,
				() -> decoder.decode(token(c -> c.issuer("https://app.example.com/realms/windfire"))));
		assertTrue(e.getErrors().stream().anyMatch(error -> error.getDescription().contains("iss")), e.getErrors().toString());
	}

	@Test
	@DisplayName("Right issuer without the API audience is rejected")
	@Does("Validly signed token with the right iss but aud = [account] → JwtValidationException on aud")
	void missingAudienceIsRejected() {
		JwtValidationException e = assertThrows(JwtValidationException.class,
				() -> decoder.decode(token(c -> c.audience(List.of("account")))));
		assertTrue(e.getErrors().stream().anyMatch(error -> error.getDescription().contains("aud")), e.getErrors().toString());
	}

}

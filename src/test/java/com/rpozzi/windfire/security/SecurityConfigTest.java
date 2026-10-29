package com.rpozzi.windfire.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rpozzi.windfire.common.web.RestExceptionHandler;
import com.rpozzi.windfire.restaurants.service.RestaurantRESTService;
import com.rpozzi.windfire.restaurants.service.RestaurantService;

/**
 * Role-based authorization, audience validation and CORS, exercised against the real SecurityConfig.
 */
@WebMvcTest(RestaurantRESTService.class)
@ContextConfiguration(classes = { RestaurantRESTService.class, RestExceptionHandler.class, SecurityConfig.class,
		CustomAuthenticationEntryPoint.class })
@TestPropertySource(properties = { "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + SecurityConfigTest.ISSUER,
		"windfire.cors.allowed-origins=https://allowed.example, https://other.example" })
class SecurityConfigTest {

	static final String ISSUER = "https://localhost/realms/test";
	private static final String API_CLIENT_ID = "windfire-restaurants-api";
	private static final SecretKey KEY = new SecretKeySpec(
			"test-only-hmac-key-of-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtAuthenticationConverter jwtAuthenticationConverter;
	@MockBean
	private RestaurantService restaurantService;
	// The production decoder discovers Keycloak's JWKS from the issuer; here it is replaced by an HMAC decoder
	// that uses the same validator as production
	@MockBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void delegateToHmacDecoder() {
		NimbusJwtDecoder hmacDecoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
		hmacDecoder.setJwtValidator(SecurityConfig.jwtValidator(ISSUER, API_CLIENT_ID));
		when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> hmacDecoder.decode(invocation.getArgument(0)));
	}

	private static Map<String, Object> resourceAccess(String role) {
		return Map.of(API_CLIENT_ID, Map.of("roles", List.of(role)));
	}

	/** A mock JWT whose authorities come from the real JwtAuthenticationConverter. */
	private JwtRequestPostProcessor tokenWithRole(String role) {
		return jwt().jwt(jwt -> jwt.claim("resource_access", resourceAccess(role)))
				.authorities(jwt -> jwtAuthenticationConverter.convert(jwt).getAuthorities());
	}

	private static String signedToken(List<String> audience) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject("user")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim("resource_access", resourceAccess("restaurants-reader"));
		if (audience != null) {
			claims.audience(audience);
		}
		return new NimbusJwtEncoder(new ImmutableSecret<>(KEY))
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
				.getTokenValue();
	}

	private static final String RESTAURANT_JSON = "{\"name\":\"Battipalo\","
			+ "\"address\":{\"zipCode\":\"28040\",\"city\":\"Lesa\",\"street\":\"Viale Vittorio Veneto, 2\","
			+ "\"province\":\"NO\",\"region\":\"Piemonte\",\"country\":\"Italia\"},"
			+ "\"cuisine\":\"Italian\",\"phone\":\"0321713829\",\"email\":\"battipalo@example.com\","
			+ "\"website\":\"https://example.com\"}";

	@Test
	void readerCanGet() throws Exception {
		mockMvc.perform(get("/restaurants").with(tokenWithRole("restaurants-reader")))
				.andExpect(status().isOk());
		mockMvc.perform(get("/restaurants/1").with(tokenWithRole("restaurants-reader")))
				.andExpect(status().isOk());
	}

	@Test
	void readerCannotPost() throws Exception {
		mockMvc.perform(post("/restaurants").with(tokenWithRole("restaurants-reader")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(RESTAURANT_JSON))
				.andExpect(status().isForbidden());

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	void readerCannotDelete() throws Exception {
		mockMvc.perform(delete("/restaurants/1").with(tokenWithRole("restaurants-reader")).with(csrf()))
				.andExpect(status().isForbidden());

		verify(restaurantService, never()).removeRestaurant(any());
	}

	@Test
	void writerCanPost() throws Exception {
		when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/restaurants").with(tokenWithRole("restaurants-writer")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(RESTAURANT_JSON))
				.andExpect(status().isOk());

		verify(restaurantService).addRestaurant(any());
	}

	@Test
	void tokenWithoutApiAudienceIsUnauthorized() throws Exception {
		mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken(null)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
		mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken(List.of("account"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithApiAudienceIsAccepted() throws Exception {
		mockMvc.perform(get("/restaurants")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken(List.of("account", API_CLIENT_ID))))
				.andExpect(status().isOk());
	}

	@Test
	void preflightFromUnlistedOriginIsRejected() throws Exception {
		mockMvc.perform(options("/restaurants")
				.header(HttpHeaders.ORIGIN, "https://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void preflightFromListedOriginIsAllowed() throws Exception {
		mockMvc.perform(options("/restaurants")
				.header(HttpHeaders.ORIGIN, "https://other.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://other.example"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

}

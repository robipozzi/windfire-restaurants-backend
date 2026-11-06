package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import com.rpozzi.windfire.restaurants.entities.Restaurant;
import com.rpozzi.windfire.restaurants.service.RestaurantRESTService;
import com.rpozzi.windfire.restaurants.service.RestaurantService;
import com.rpozzi.windfire.testsupport.Does;

/**
 * Role-based authorization, audience validation and CORS, exercised against the real SecurityConfig.
 */
@WebMvcTest(RestaurantRESTService.class)
@ContextConfiguration(classes = { RestaurantRESTService.class, RestExceptionHandler.class, SecurityConfig.class,
		CustomAuthenticationEntryPoint.class })
@TestPropertySource(properties = { "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + SecurityConfigTest.ISSUER,
		"windfire.cors.allowed-origins=https://allowed.example, https://other.example",
		// Pin the secure value (also the application.properties default) so PublicSwagger can flip it
		"windfire.swagger.public=false" })
@DisplayName("Security")
class SecurityConfigTest {

	static final String ISSUER = "https://localhost/realms/test";
	private static final String API_CLIENT_ID = "windfire-restaurants-api";
	private static final SecretKey KEY = hmacKey("test-only-hmac-key-of-at-least-32-bytes!");
	private static final SecretKey OTHER_KEY = hmacKey("another-hmac-key-nobody-should-trust!!");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtAuthenticationConverter jwtAuthenticationConverter;
	@MockitoBean
	private RestaurantService restaurantService;
	// The production decoder discovers Keycloak's JWKS from the issuer; here it is replaced by an HMAC decoder
	// that uses the same validator as production
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void delegateToHmacDecoder() {
		NimbusJwtDecoder hmacDecoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
		hmacDecoder.setJwtValidator(SecurityConfig.jwtValidator(ISSUER, API_CLIENT_ID));
		doAnswer(invocation -> hmacDecoder.decode(invocation.getArgument(0))).when(jwtDecoder).decode(anyString());
	}

	private static SecretKey hmacKey(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private static Map<String, Object> resourceAccess(String role) {
		return Map.of(API_CLIENT_ID, Map.of("roles", List.of(role)));
	}

	/** A mock JWT whose authorities come from the real JwtAuthenticationConverter. */
	private JwtRequestPostProcessor tokenWithRole(String role) {
		return tokenWithClaims(jwt -> jwt.claim("resource_access", resourceAccess(role)));
	}

	private JwtRequestPostProcessor tokenWithClaims(Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> claims) {
		return jwt().jwt(claims).authorities(jwt -> jwtAuthenticationConverter.convert(jwt).getAuthorities());
	}

	/** A reader token with the API audience, signed with KEY; the customizer adjusts it for each case. */
	private static String signedToken(Consumer<JwtClaimsSet.Builder> customizer) {
		return signedToken(KEY, customizer);
	}

	private static String signedToken(SecretKey key, Consumer<JwtClaimsSet.Builder> customizer) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject("user")
				.audience(List.of("account", API_CLIENT_ID))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim("resource_access", resourceAccess("restaurants-reader"));
		customizer.accept(claims);
		return new NimbusJwtEncoder(new ImmutableSecret<>(key))
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
				.getTokenValue();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static final String RESTAURANT_JSON = "{\"name\":\"Battipalo\","
			+ "\"address\":{\"zipCode\":\"28040\",\"city\":\"Lesa\",\"street\":\"Viale Vittorio Veneto, 2\","
			+ "\"province\":\"NO\",\"region\":\"Piemonte\",\"country\":\"Italia\"},"
			+ "\"cuisine\":\"Italian\",\"phone\":\"0321713829\",\"email\":\"battipalo@example.com\","
			+ "\"website\":\"https://example.com\"}";

	@Nested
	@DisplayName("Authentication")
	class Authentication {

		@Test
		@DisplayName("Request without a token is unauthorized")
		@Does("GET /restaurants with no Authorization header → 401 {error: unauthorized, message: No Bearer token provided}")
		void missingTokenIsUnauthorized() throws Exception {
			mockMvc.perform(get("/restaurants"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthorized"))
					.andExpect(jsonPath("$.message").value("No Bearer token provided"));
		}

		@Test
		@DisplayName("Token without the API audience is unauthorized")
		@Does("GET /restaurants with a signed token whose aud is missing or only 'account' → 401 Invalid Bearer token provided")
		void tokenWithoutApiAudienceIsUnauthorized() throws Exception {
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(signedToken(c -> c.claims(m -> m.remove("aud"))))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(signedToken(c -> c.audience(List.of("account"))))))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("Token with the API audience is accepted")
		@Does("GET /restaurants with a valid signed reader token (aud = account + API client) → 200")
		void tokenWithApiAudienceIsAccepted() throws Exception {
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(signedToken(c -> { }))))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("Expired token is unauthorized")
		@Does("GET /restaurants with a token that expired 2 minutes ago (beyond the 60 s clock skew) → 401 Invalid Bearer token provided")
		void expiredTokenIsUnauthorized() throws Exception {
			String expired = signedToken(c -> c.issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(120)));
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
		}

		@Test
		@DisplayName("Token from another issuer is unauthorized")
		@Does("GET /restaurants with a token whose iss is a different realm → 401 Invalid Bearer token provided")
		void tokenFromOtherIssuerIsUnauthorized() throws Exception {
			String foreign = signedToken(c -> c.issuer("https://localhost/realms/other"));
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(foreign)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
		}

		@Test
		@DisplayName("Token signed with an untrusted key is unauthorized")
		@Does("GET /restaurants with an otherwise valid token signed by a different key → 401 Invalid Bearer token provided")
		void tokenWithBadSignatureIsUnauthorized() throws Exception {
			String forged = signedToken(OTHER_KEY, c -> { });
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
		}

		@Test
		@DisplayName("Malformed token is unauthorized")
		@Does("GET /restaurants with 'Bearer not-a-jwt' → 401 Invalid Bearer token provided")
		void malformedTokenIsUnauthorized() throws Exception {
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
		}

		@Test
		@DisplayName("Empty Bearer header is unauthorized")
		@Does("GET /restaurants with 'Bearer ' and no token → 401 Invalid Bearer token provided")
		void emptyBearerIsUnauthorized() throws Exception {
			mockMvc.perform(get("/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer "))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid Bearer token provided"));
		}

		@Test
		@DisplayName("Bearer-token requests need no CSRF token")
		@Does("POST /restaurants with a real writer Bearer token and no CSRF token → 200 (CSRF only applies to cookie sessions)")
		void bearerRequestNeedsNoCsrf() throws Exception {
			when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));
			String writer = signedToken(c -> c.claim("resource_access", resourceAccess("restaurants-writer")));

			mockMvc.perform(post("/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(writer))
					.contentType(MediaType.APPLICATION_JSON)
					.content(RESTAURANT_JSON))
					.andExpect(status().isOk());
		}

	}

	@Nested
	@DisplayName("Authorization")
	class Authorization {

		@Test
		@DisplayName("Reader can GET")
		@Does("GET /restaurants and /restaurants/1 with a restaurants-reader token → 200")
		void readerCanGet() throws Exception {
			mockMvc.perform(get("/restaurants").with(tokenWithRole("restaurants-reader")))
					.andExpect(status().isOk());
			mockMvc.perform(get("/restaurants/1").with(tokenWithRole("restaurants-reader")))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("Reader cannot POST")
		@Does("POST /restaurants with a restaurants-reader token → 403, service never called")
		void readerCannotPost() throws Exception {
			mockMvc.perform(post("/restaurants").with(tokenWithRole("restaurants-reader")).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(RESTAURANT_JSON))
					.andExpect(status().isForbidden());

			verify(restaurantService, never()).addRestaurant(any());
		}

		@Test
		@DisplayName("Reader cannot DELETE")
		@Does("DELETE /restaurants/1 with a restaurants-reader token → 403, service never called")
		void readerCannotDelete() throws Exception {
			mockMvc.perform(delete("/restaurants/1").with(tokenWithRole("restaurants-reader")).with(csrf()))
					.andExpect(status().isForbidden());

			verify(restaurantService, never()).removeRestaurant(any());
		}

		@Test
		@DisplayName("Writer can GET")
		@Does("GET /restaurants and /restaurants/1 with a restaurants-writer token → 200 (writers read too)")
		void writerCanGet() throws Exception {
			mockMvc.perform(get("/restaurants").with(tokenWithRole("restaurants-writer")))
					.andExpect(status().isOk());
			mockMvc.perform(get("/restaurants/1").with(tokenWithRole("restaurants-writer")))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("Writer can POST")
		@Does("POST /restaurants with a restaurants-writer token → 200, service called once")
		void writerCanPost() throws Exception {
			when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));

			mockMvc.perform(post("/restaurants").with(tokenWithRole("restaurants-writer")).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(RESTAURANT_JSON))
					.andExpect(status().isOk());

			verify(restaurantService).addRestaurant(any());
		}

		@Test
		@DisplayName("Writer can DELETE")
		@Does("DELETE /restaurants/42 with a restaurants-writer token → 200, service asked to remove id 42")
		void writerCanDelete() throws Exception {
			mockMvc.perform(delete("/restaurants/42").with(tokenWithRole("restaurants-writer")).with(csrf()))
					.andExpect(status().isOk());

			ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
			verify(restaurantService).removeRestaurant(captor.capture());
			assertEquals("42", captor.getValue().getId());
		}

		@Test
		@DisplayName("Authenticated token without any role is forbidden")
		@Does("GET /restaurants with a valid token that has no resource_access claim → 403")
		void tokenWithoutRoleIsForbidden() throws Exception {
			mockMvc.perform(get("/restaurants").with(tokenWithClaims(jwt -> jwt.claim("sub", "nobody"))))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("Realm role with the same name grants nothing")
		@Does("GET /restaurants with restaurants-reader only in realm_access.roles (not the API client) → 403")
		void realmRoleIsIgnored() throws Exception {
			mockMvc.perform(get("/restaurants")
					.with(tokenWithClaims(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("restaurants-reader"))))))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("Role granted for another client grants nothing")
		@Does("GET /restaurants with restaurants-writer under resource_access.other-client → 403")
		void otherClientRoleIsIgnored() throws Exception {
			mockMvc.perform(get("/restaurants").with(tokenWithClaims(
					jwt -> jwt.claim("resource_access", Map.of("other-client", Map.of("roles", List.of("restaurants-writer")))))))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("Unmapped method is rejected after authentication")
		@Does("PUT /restaurants/1 with a writer token → 405; without a token → 401 (anyRequest().authenticated())")
		void unmappedMethodNeedsAuthentication() throws Exception {
			mockMvc.perform(put("/restaurants/1").with(tokenWithRole("restaurants-writer")).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(RESTAURANT_JSON))
					.andExpect(status().isMethodNotAllowed());
			mockMvc.perform(put("/restaurants/1").with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(RESTAURANT_JSON))
					.andExpect(status().isUnauthorized());
		}

	}

	@Nested
	@DisplayName("CORS")
	class Cors {

		@Test
		@DisplayName("Preflight from an unlisted origin is rejected")
		@Does("OPTIONS /restaurants from https://evil.example → 403, no Access-Control-Allow-Origin header")
		void preflightFromUnlistedOriginIsRejected() throws Exception {
			mockMvc.perform(options("/restaurants")
					.header(HttpHeaders.ORIGIN, "https://evil.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
					.andExpect(status().isForbidden())
					.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		}

		@Test
		@DisplayName("Preflight from a listed origin is allowed")
		@Does("OPTIONS /restaurants from https://other.example (listed after ', ' → trimmed) asking POST + Authorization → 200, origin echoed, no credentials")
		void preflightFromListedOriginIsAllowed() throws Exception {
			mockMvc.perform(options("/restaurants")
					.header(HttpHeaders.ORIGIN, "https://other.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
					.andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://other.example"))
					.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
		}

		@Test
		@DisplayName("Preflight for a method that is not allowed is rejected")
		@Does("OPTIONS /restaurants from a listed origin asking PUT → 403")
		void preflightWithDisallowedMethodIsRejected() throws Exception {
			mockMvc.perform(options("/restaurants")
					.header(HttpHeaders.ORIGIN, "https://allowed.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("Preflight for a header that is not allowed is rejected")
		@Does("OPTIONS /restaurants from a listed origin asking header X-Custom → 403")
		void preflightWithDisallowedHeaderIsRejected() throws Exception {
			mockMvc.perform(options("/restaurants")
					.header(HttpHeaders.ORIGIN, "https://allowed.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom"))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("Actual request from a listed origin carries the CORS header")
		@Does("GET /restaurants with a reader token and Origin https://allowed.example → 200 with Access-Control-Allow-Origin echoed")
		void actualRequestFromListedOriginGetsHeader() throws Exception {
			mockMvc.perform(get("/restaurants").with(tokenWithRole("restaurants-reader"))
					.header(HttpHeaders.ORIGIN, "https://allowed.example"))
					.andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://allowed.example"));
		}

	}

	@Nested
	@DisplayName("Swagger not public")
	class PrivateSwagger {

		@Test
		@DisplayName("API docs require a token")
		@Does("GET /v3/api-docs and /swagger-ui/index.html without a token (windfire.swagger.public=false) → 401")
		void swaggerRequiresTokenWhenNotPublic() throws Exception {
			mockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isUnauthorized());
			mockMvc.perform(get("/swagger-ui/index.html"))
					.andExpect(status().isUnauthorized());
		}

	}

	// springdoc is not part of the web slice, so a 404 here means the request got past security
	@Nested
	@TestPropertySource(properties = "windfire.swagger.public=true")
	@DisplayName("Swagger public")
	class PublicSwagger {

		// The enclosing instance's MockMvc belongs to the outer context, where Swagger is not public
		@Autowired
		private MockMvc publicSwaggerMockMvc;

		@Test
		@DisplayName("API docs are reachable without a token")
		@Does("GET /v3/api-docs and /swagger-ui/index.html without a token (windfire.swagger.public=true) → past security (404 in the web slice)")
		void swaggerIsReachableWithoutToken() throws Exception {
			publicSwaggerMockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isNotFound());
			publicSwaggerMockMvc.perform(get("/swagger-ui/index.html"))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("Restaurants still require a token")
		@Does("GET /restaurants without a token while Swagger is public → 401")
		void restaurantsStillRequireToken() throws Exception {
			publicSwaggerMockMvc.perform(get("/restaurants"))
					.andExpect(status().isUnauthorized());
		}

	}

}

package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import com.rpozzi.windfire.testsupport.Does;

/**
 * Keycloak client roles (resource_access.&lt;api-client-id&gt;.roles) → ROLE_ authorities, without Spring.
 */
@DisplayName("Security · Keycloak role mapping")
class JwtRoleMappingTest {

	private static final String API_CLIENT_ID = "windfire-restaurants-api";
	// Added by Spring Security 7 to every authenticated JWT: records how the user authenticated, grants no API access
	private static final String BEARER_FACTOR = "FACTOR_BEARER";

	private static Set<String> authorities(Consumer<Map<String, Object>> claims) {
		SecurityConfig securityConfig = new SecurityConfig();
		ReflectionTestUtils.setField(securityConfig, "apiClientId", API_CLIENT_ID);
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user").claims(claims).build();
		return securityConfig.jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
	}

	@Test
	@DisplayName("Client role becomes a ROLE_ authority")
	@Does("resource_access.windfire-restaurants-api.roles = [restaurants-reader] → {FACTOR_BEARER, ROLE_restaurants-reader}")
	void clientRoleIsMapped() {
		assertEquals(Set.of(BEARER_FACTOR, "ROLE_restaurants-reader"),
				authorities(c -> c.put("resource_access", Map.of(API_CLIENT_ID, Map.of("roles", List.of("restaurants-reader"))))));
	}

	@Test
	@DisplayName("Every client role is mapped")
	@Does("roles = [restaurants-reader, restaurants-writer] → FACTOR_BEARER and both ROLE_ authorities")
	void multipleRolesAreMapped() {
		assertEquals(Set.of(BEARER_FACTOR, "ROLE_restaurants-reader", "ROLE_restaurants-writer"), authorities(c -> c.put("resource_access",
				Map.of(API_CLIENT_ID, Map.of("roles", List.of("restaurants-reader", "restaurants-writer"))))));
	}

	@Test
	@DisplayName("No resource_access claim → no role authorities")
	@Does("Token with only sub → only FACTOR_BEARER")
	void missingResourceAccessGivesNothing() {
		assertEquals(Set.of(BEARER_FACTOR), authorities(c -> { }));
	}

	@Test
	@DisplayName("Roles of another client are ignored")
	@Does("resource_access.other-client.roles = [restaurants-writer] → only FACTOR_BEARER")
	void otherClientRolesAreIgnored() {
		assertEquals(Set.of(BEARER_FACTOR),
				authorities(c -> c.put("resource_access", Map.of("other-client", Map.of("roles", List.of("restaurants-writer"))))));
	}

	@Test
	@DisplayName("Realm roles are ignored")
	@Does("realm_access.roles = [restaurants-writer] only → only FACTOR_BEARER")
	void realmRolesAreIgnored() {
		assertEquals(Set.of(BEARER_FACTOR), authorities(c -> c.put("realm_access", Map.of("roles", List.of("restaurants-writer")))));
	}

	@Test
	@DisplayName("Malformed resource_access is tolerated")
	@Does("Client entry that is a string, or roles that is a string → only FACTOR_BEARER, no exception")
	void malformedClaimGivesNothing() {
		assertEquals(Set.of(BEARER_FACTOR), authorities(c -> c.put("resource_access", Map.of(API_CLIENT_ID, "restaurants-writer"))));
		assertEquals(Set.of(BEARER_FACTOR), authorities(c -> c.put("resource_access", Map.of(API_CLIENT_ID, Map.of("roles", "restaurants-writer")))));
	}

}

package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	@Does("resource_access.windfire-restaurants-api.roles = [restaurants-reader] → {ROLE_restaurants-reader}")
	void clientRoleIsMapped() {
		assertEquals(Set.of("ROLE_restaurants-reader"),
				authorities(c -> c.put("resource_access", Map.of(API_CLIENT_ID, Map.of("roles", List.of("restaurants-reader"))))));
	}

	@Test
	@DisplayName("Every client role is mapped")
	@Does("roles = [restaurants-reader, restaurants-writer] → both ROLE_ authorities")
	void multipleRolesAreMapped() {
		assertEquals(Set.of("ROLE_restaurants-reader", "ROLE_restaurants-writer"), authorities(c -> c.put("resource_access",
				Map.of(API_CLIENT_ID, Map.of("roles", List.of("restaurants-reader", "restaurants-writer"))))));
	}

	@Test
	@DisplayName("No resource_access claim → no authorities")
	@Does("Token with only sub → empty authority set")
	void missingResourceAccessGivesNothing() {
		assertTrue(authorities(c -> { }).isEmpty());
	}

	@Test
	@DisplayName("Roles of another client are ignored")
	@Does("resource_access.other-client.roles = [restaurants-writer] → empty authority set")
	void otherClientRolesAreIgnored() {
		assertTrue(authorities(c -> c.put("resource_access", Map.of("other-client", Map.of("roles", List.of("restaurants-writer")))))
				.isEmpty());
	}

	@Test
	@DisplayName("Realm roles are ignored")
	@Does("realm_access.roles = [restaurants-writer] only → empty authority set")
	void realmRolesAreIgnored() {
		assertTrue(authorities(c -> c.put("realm_access", Map.of("roles", List.of("restaurants-writer")))).isEmpty());
	}

	@Test
	@DisplayName("Malformed resource_access is tolerated")
	@Does("Client entry that is a string, or roles that is a string → empty authority set, no exception")
	void malformedClaimGivesNothing() {
		assertTrue(authorities(c -> c.put("resource_access", Map.of(API_CLIENT_ID, "restaurants-writer"))).isEmpty());
		assertTrue(authorities(c -> c.put("resource_access", Map.of(API_CLIENT_ID, Map.of("roles", "restaurants-writer")))).isEmpty());
	}

}

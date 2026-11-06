package com.rpozzi.windfire;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.rpozzi.windfire.application.WindfireApplication;
import com.rpozzi.windfire.restaurants.entities.Address;
import com.rpozzi.windfire.restaurants.entities.IRestaurantRepository;
import com.rpozzi.windfire.restaurants.entities.Restaurant;
import com.rpozzi.windfire.testsupport.Does;

/**
 * The whole application as deployed on the Raspberry (no profile), with only Keycloak and MongoDB replaced.
 */
@SpringBootTest(classes = WindfireApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Application · Full context (no profile)")
class ApplicationContextTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private IRestaurantRepository repository;

	@Test
	@DisplayName("GET /restaurants goes through controller, service and DAO")
	@Does("Repository holds one restaurant → GET /restaurants with a reader token → 200 with that restaurant (real wiring)")
	void requestFlowsThroughAllLayers() throws Exception {
		Restaurant restaurant = new Restaurant("Battipalo", new Address("28040", "Lesa", "Viale Vittorio Veneto, 2", "NO",
				"Piemonte", "Italia"), "Italian", null, null, null, null);
		restaurant.setId("1");
		when(repository.findAll()).thenReturn(List.of(restaurant));

		mockMvc.perform(get("/restaurants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-reader"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Battipalo"));
	}

	@Test
	@DisplayName("GET /restaurants/{unknown} answers 404 end to end")
	@Does("Repository finds nothing for 'missing' → GET /restaurants/missing → 404 {error: not_found} (was 500 before the fix)")
	void unknownIdIsNotFound() throws Exception {
		when(repository.findById("missing")).thenReturn(Optional.empty());

		mockMvc.perform(get("/restaurants/missing").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-reader"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	@DisplayName("Health endpoint is public")
	@Does("GET /actuator/health without a token → 200 {status: UP}")
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("Other actuator endpoints are not public")
	@Does("GET /actuator (endpoint index) without a token → 401")
	void actuatorIndexNeedsToken() throws Exception {
		mockMvc.perform(get("/actuator"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Swagger is not served without the dev profile")
	@Does("GET /v3/api-docs with a valid token → 404 (springdoc disabled by default)")
	void swaggerIsDisabled() throws Exception {
		mockMvc.perform(get("/v3/api-docs").with(jwt()))
				.andExpect(status().isNotFound());
	}

}

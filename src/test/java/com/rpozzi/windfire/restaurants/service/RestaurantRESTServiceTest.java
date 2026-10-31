package com.rpozzi.windfire.restaurants.service;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.rpozzi.windfire.common.dao.ResourceNotFoundException;
import com.rpozzi.windfire.common.web.RestExceptionHandler;
import com.rpozzi.windfire.restaurants.entities.Address;
import com.rpozzi.windfire.restaurants.entities.Restaurant;
import com.rpozzi.windfire.security.CustomAuthenticationEntryPoint;
import com.rpozzi.windfire.security.SecurityConfig;
import com.rpozzi.windfire.testsupport.Does;

/**
 * Controller behaviour (status codes, JSON shape, what reaches the service), with authorization out of the way.
 */
@WebMvcTest(RestaurantRESTService.class)
@ContextConfiguration(classes = { RestaurantRESTService.class, RestExceptionHandler.class, SecurityConfig.class,
		CustomAuthenticationEntryPoint.class })
@TestPropertySource(properties = { "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost/realms/test",
		"windfire.cors.allowed-origins=https://allowed.example" })
@DisplayName("Restaurants · REST endpoints")
class RestaurantRESTServiceTest {

	@Autowired
	private MockMvc mockMvc;
	@MockBean
	private RestaurantService restaurantService;
	@MockBean
	private JwtDecoder jwtDecoder;

	private static JwtRequestPostProcessor writer() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-writer"));
	}

	private static Restaurant restaurant(String id, String name) {
		Restaurant restaurant = new Restaurant(name, new Address("28040", "Lesa", "Viale Vittorio Veneto, 2", "NO", "Piemonte", "Italia"),
				"Italian", "0321713829", null, "battipalo@example.com", "https://example.com");
		restaurant.setId(id);
		return restaurant;
	}

	@Test
	@DisplayName("GET /restaurants returns the list as JSON")
	@Does("Service returns 2 restaurants → 200, JSON array of 2 with id, name and nested address.city")
	void listRestaurants() throws Exception {
		when(restaurantService.findAllRestaurants()).thenReturn(List.of(restaurant("1", "Battipalo"), restaurant("2", "Taverna")));

		mockMvc.perform(get("/restaurants").with(writer()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value("1"))
				.andExpect(jsonPath("$[1].name").value("Taverna"))
				.andExpect(jsonPath("$[0].address.city").value("Lesa"));
	}

	@Test
	@DisplayName("GET /restaurants with no data returns []")
	@Does("Service returns an empty list → 200 with body []")
	void listEmpty() throws Exception {
		when(restaurantService.findAllRestaurants()).thenReturn(List.of());

		mockMvc.perform(get("/restaurants").with(writer()))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	@DisplayName("GET /restaurants/{id} returns the restaurant")
	@Does("Service returns restaurant 1 → 200 with its id, name and website")
	void getById() throws Exception {
		when(restaurantService.findRestaurantById("1")).thenReturn(restaurant("1", "Battipalo"));

		mockMvc.perform(get("/restaurants/1").with(writer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("1"))
				.andExpect(jsonPath("$.name").value("Battipalo"))
				.andExpect(jsonPath("$.website").value("https://example.com"));
	}

	@Test
	@DisplayName("GET /restaurants/{unknown} returns 404")
	@Does("Service throws ResourceNotFoundException → 404 {error: not_found}, the requested id is not echoed")
	void getUnknownIsNotFound() throws Exception {
		when(restaurantService.findRestaurantById("no-such-id")).thenThrow(new ResourceNotFoundException("no-such-id"));

		mockMvc.perform(get("/restaurants/no-such-id").with(writer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"))
				.andExpect(jsonPath("$.message").value("Resource not found"))
				.andExpect(content().string(not(containsString("no-such-id"))));
	}

	@Test
	@DisplayName("POST /restaurants returns the stored restaurant")
	@Does("Service stores the restaurant and assigns id 'generated-id' → 200 with that id in the body")
	void postReturnsStoredRestaurant() throws Exception {
		when(restaurantService.addRestaurant(any())).thenReturn(restaurant("generated-id", "Battipalo"));

		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Battipalo\",\"address\":{\"city\":\"Lesa\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("generated-id"))
				.andExpect(jsonPath("$.name").value("Battipalo"));
	}

	@Test
	@DisplayName("DELETE /restaurants/{id} removes the restaurant")
	@Does("DELETE /restaurants/42 → 200 with empty body, service receives a Restaurant whose id is 42")
	void deleteRemovesRestaurant() throws Exception {
		mockMvc.perform(delete("/restaurants/42").with(writer()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(""));

		ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
		verify(restaurantService).removeRestaurant(captor.capture());
		assertEquals("42", captor.getValue().getId());
	}

	@Test
	@DisplayName("DELETE /restaurants/{unknown} returns 404")
	@Does("Service throws ResourceNotFoundException → 404 {error: not_found}")
	void deleteUnknownIsNotFound() throws Exception {
		doThrow(new ResourceNotFoundException("no-such-id")).when(restaurantService).removeRestaurant(any());

		mockMvc.perform(delete("/restaurants/no-such-id").with(writer()).with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
	}

}

package com.rpozzi.windfire.restaurants.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.rpozzi.windfire.common.web.RestExceptionHandler;
import com.rpozzi.windfire.restaurants.entities.Restaurant;
import com.rpozzi.windfire.security.CustomAuthenticationEntryPoint;
import com.rpozzi.windfire.security.SecurityConfig;

/**
 * WindfireApplication lives in a sibling package and component-scans the DAO and Mongo repositories,
 * so the web slice is wired explicitly instead.
 */
@WebMvcTest(RestaurantRESTService.class)
@ContextConfiguration(classes = { RestaurantRESTService.class, RestExceptionHandler.class, SecurityConfig.class,
		CustomAuthenticationEntryPoint.class })
// application.properties builds the issuer-uri from Keycloak env vars that are not set in tests; the JwtDecoder is mocked anyway
@TestPropertySource(properties = { "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost/realms/test",
		"windfire.cors.allowed-origins=https://allowed.example" })
class RestaurantRESTServiceValidationTest {

	@Autowired
	private MockMvc mockMvc;
	@MockBean
	private RestaurantService restaurantService;
	@MockBean
	private JwtDecoder jwtDecoder;

	private static String restaurantJson(String id, String website) {
		String idField = id == null ? "" : "\"id\":\"" + id + "\",";
		return "{" + idField + "\"name\":\"Battipalo\","
				+ "\"address\":{\"zipCode\":\"28040\",\"city\":\"Lesa\",\"street\":\"Viale Vittorio Veneto, 2\","
				+ "\"province\":\"NO\",\"region\":\"Piemonte\",\"country\":\"Italia\"},"
				+ "\"cuisine\":\"Italian\",\"phone\":\"0321713829\",\"email\":\"battipalo@example.com\","
				+ "\"website\":\"" + website + "\"}";
	}

	@Test
	void postWithJavascriptWebsiteIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-writer"))).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(restaurantJson(null, "javascript:alert(1)")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("website"))
				.andExpect(content().string(not(containsString("javascript:alert(1)"))));

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	void postWithHttpsWebsitePassesValidationAndIgnoresClientId() throws Exception {
		when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/restaurants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-writer"))).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(restaurantJson("existing-id", "https://example.com")))
				.andExpect(status().isOk());

		ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
		verify(restaurantService).addRestaurant(captor.capture());
		assertNull(captor.getValue().getId());
		assertEquals("https://example.com", captor.getValue().getWebsite());
	}


	@Test
	void postWithoutAddressIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-writer"))).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Battipalo\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("address"));

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	void postWithEmptyWebsiteAndEmailFromTheUiIsAccepted() throws Exception {
		when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/restaurants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-writer"))).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(restaurantJson(null, "").replace("battipalo@example.com", "")))
				.andExpect(status().isOk());
	}

}

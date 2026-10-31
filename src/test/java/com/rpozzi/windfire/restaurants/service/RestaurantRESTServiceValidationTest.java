package com.rpozzi.windfire.restaurants.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.rpozzi.windfire.common.web.RestExceptionHandler;
import com.rpozzi.windfire.restaurants.entities.Restaurant;
import com.rpozzi.windfire.security.CustomAuthenticationEntryPoint;
import com.rpozzi.windfire.security.SecurityConfig;
import com.rpozzi.windfire.testsupport.Does;

/**
 * WindfireApplication lives in a sibling package and component-scans the DAO and Mongo repositories,
 * so the web slice is wired explicitly instead. Field-by-field boundaries live in RestaurantValidationTest;
 * this class checks that validation is applied to request bodies and how errors are reported.
 */
@WebMvcTest(RestaurantRESTService.class)
@ContextConfiguration(classes = { RestaurantRESTService.class, RestExceptionHandler.class, SecurityConfig.class,
		CustomAuthenticationEntryPoint.class })
// application.properties builds the issuer-uri from Keycloak env vars that are not set in tests; the JwtDecoder is mocked anyway
@TestPropertySource(properties = { "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost/realms/test",
		"windfire.cors.allowed-origins=https://allowed.example" })
@DisplayName("Restaurants · Request validation")
class RestaurantRESTServiceValidationTest {

	@Autowired
	private MockMvc mockMvc;
	@MockBean
	private RestaurantService restaurantService;
	@MockBean
	private JwtDecoder jwtDecoder;

	private static JwtRequestPostProcessor writer() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_restaurants-writer"));
	}

	private static String restaurantJson(String id, String website) {
		String idField = id == null ? "" : "\"id\":\"" + id + "\",";
		return "{" + idField + "\"name\":\"Battipalo\","
				+ "\"address\":{\"zipCode\":\"28040\",\"city\":\"Lesa\",\"street\":\"Viale Vittorio Veneto, 2\","
				+ "\"province\":\"NO\",\"region\":\"Piemonte\",\"country\":\"Italia\"},"
				+ "\"cuisine\":\"Italian\",\"phone\":\"0321713829\",\"email\":\"battipalo@example.com\","
				+ "\"website\":\"" + website + "\"}";
	}

	@Test
	@DisplayName("javascript: website is rejected without echoing it")
	@Does("POST with website javascript:alert(1) → 400 validation_failed, one fieldError 'website', value not in the body, service not called")
	void postWithJavascriptWebsiteIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
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
	@DisplayName("Valid POST passes and the client id is ignored")
	@Does("POST with id 'existing-id' and an https website → 200, service receives the restaurant with id null")
	void postWithHttpsWebsitePassesValidationAndIgnoresClientId() throws Exception {
		when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(restaurantJson("existing-id", "https://example.com")))
				.andExpect(status().isOk());

		ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
		verify(restaurantService).addRestaurant(captor.capture());
		assertNull(captor.getValue().getId());
		assertEquals("https://example.com", captor.getValue().getWebsite());
	}

	@Test
	@DisplayName("POST without address is rejected")
	@Does("POST {name} only → 400 with fieldError 'address', service not called")
	void postWithoutAddressIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Battipalo\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("address"));

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	@DisplayName("Empty website and email from the UI are accepted")
	@Does("POST with website \"\" and email \"\" (what the UI sends for blank fields) → 200")
	void postWithEmptyWebsiteAndEmailFromTheUiIsAccepted() throws Exception {
		when(restaurantService.addRestaurant(any())).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(restaurantJson(null, "").replace("battipalo@example.com", "")))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Every invalid field is reported, no value echoed")
	@Does("POST with blank name, bad email, ftp website, 201-char city → 400 listing name, email, website, address.city with messages; no rejected value in the body")
	void allInvalidFieldsAreReported() throws Exception {
		String longCity = "c".repeat(201);
		String body = "{\"name\":\" \",\"address\":{\"city\":\"" + longCity + "\"},"
				+ "\"email\":\"not-an-email\",\"website\":\"ftp://example.com\"}";

		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.message").value("Request body is invalid"))
				.andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("name", "email", "website", "address.city")))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'website')].message").value("must be an http(s) URL"))
				.andExpect(content().string(not(containsString("not-an-email"))))
				.andExpect(content().string(not(containsString("ftp://example.com"))))
				.andExpect(content().string(not(containsString(longCity))));

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	@DisplayName("Malformed JSON is rejected")
	@Does("POST with body '{\"name\": ' (truncated JSON) → 400, service not called")
	void malformedJsonIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": "))
				.andExpect(status().isBadRequest());

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	@DisplayName("Empty body is rejected")
	@Does("POST with Content-Type JSON and no body → 400, service not called")
	void emptyBodyIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(restaurantService, never()).addRestaurant(any());
	}

	@Test
	@DisplayName("Non-JSON content type is rejected")
	@Does("POST with Content-Type text/plain → 415 Unsupported Media Type, service not called")
	void nonJsonContentTypeIsRejected() throws Exception {
		mockMvc.perform(post("/restaurants").with(writer()).with(csrf())
				.contentType(MediaType.TEXT_PLAIN)
				.content(restaurantJson(null, "https://example.com")))
				.andExpect(status().isUnsupportedMediaType());

		verify(restaurantService, never()).addRestaurant(any());
	}

}

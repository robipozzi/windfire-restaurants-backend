package com.rpozzi.windfire.config;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.rpozzi.windfire.application.WindfireApplication;
import com.rpozzi.windfire.restaurants.entities.IRestaurantRepository;
import com.rpozzi.windfire.testsupport.Does;

/**
 * The application as started locally by run-windfire-restaurants-backend.sh (dev profile).
 */
@SpringBootTest(classes = WindfireApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "dev" })
@DisplayName("Application · Swagger with the dev profile")
class SwaggerDevProfileTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private IRestaurantRepository repository;

	@Test
	@DisplayName("OpenAPI document is public and describes the API")
	@Does("GET /v3/api-docs without a token → 200, title 'Windfire Restaurants API', paths /restaurants and /restaurants/{id}")
	void apiDocsArePublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Windfire Restaurants API"))
				.andExpect(jsonPath("$.paths", hasKey("/restaurants")))
				.andExpect(jsonPath("$.paths", hasKey("/restaurants/{id}")));
	}

	@Test
	@DisplayName("OpenAPI document declares Bearer JWT security")
	@Does("GET /v3/api-docs → components.securitySchemes.bearerAuth is type http, scheme bearer, format JWT")
	void apiDocsDeclareBearerAuth() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
	}

	@Test
	@DisplayName("Swagger UI is served without a token")
	@Does("GET /swagger-ui/index.html without a token → 200")
	void swaggerUiIsPublic() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Restaurants still require a token")
	@Does("GET /restaurants without a token in the dev profile → 401")
	void restaurantsStillRequireToken() throws Exception {
		mockMvc.perform(get("/restaurants"))
				.andExpect(status().isUnauthorized());
	}

}

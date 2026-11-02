package com.rpozzi.windfire.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpozzi.windfire.testsupport.Does;

@DisplayName("Security · 401 response body")
class CustomAuthenticationEntryPointTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);

	private Map<String, String> commence(AuthenticationException exception, MockHttpServletResponse response) throws Exception {
		entryPoint.commence(new MockHttpServletRequest(), response, exception);
		return objectMapper.readValue(response.getContentAsString(), new TypeReference<Map<String, String>>() { });
	}

	@Test
	@DisplayName("Missing token → 'No Bearer token provided'")
	@Does("InsufficientAuthenticationException → 401, application/json, {error: unauthorized, message: No Bearer token provided}")
	void missingToken() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		Map<String, String> body = commence(new InsufficientAuthenticationException("no token"), response);

		assertEquals(401, response.getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
		assertEquals(Map.of("error", "unauthorized", "message", "No Bearer token provided"), body);
	}

	@Test
	@DisplayName("Invalid token → 'Invalid Bearer token provided'")
	@Does("InvalidBearerTokenException → 401, {error: unauthorized, message: Invalid Bearer token provided}")
	void invalidToken() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		Map<String, String> body = commence(new InvalidBearerTokenException("bad"), response);

		assertEquals(401, response.getStatus());
		assertEquals(Map.of("error", "unauthorized", "message", "Invalid Bearer token provided"), body);
	}

	@Test
	@DisplayName("Malformed Bearer header → 'Invalid Bearer token provided'")
	@Does("OAuth2AuthenticationException(invalid_token), as thrown for 'Bearer ' with no token → 401, {error: unauthorized, message: Invalid Bearer token provided}")
	void malformedBearerHeader() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		Map<String, String> body = commence(new OAuth2AuthenticationException(BearerTokenErrors.invalidToken("Bearer token is malformed")), response);

		assertEquals(401, response.getStatus());
		assertEquals(Map.of("error", "unauthorized", "message", "Invalid Bearer token provided"), body);
	}

	@Test
	@DisplayName("Other failures → 401 with an empty message")
	@Does("BadCredentialsException → 401, {error: unauthorized, message: \"\"} (exception text is not leaked)")
	void otherFailure() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		Map<String, String> body = commence(new BadCredentialsException("secret detail"), response);

		assertEquals(401, response.getStatus());
		assertEquals(Map.of("error", "unauthorized", "message", ""), body);
	}

}

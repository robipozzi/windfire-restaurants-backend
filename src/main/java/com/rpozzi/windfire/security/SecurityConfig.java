package com.rpozzi.windfire.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	static final String KEYCLOAK_SSL_BUNDLE = "keycloak";

	@Autowired
    private CustomAuthenticationEntryPoint authenticationEntryPoint;

	@Value("${windfire.security.api-client-id:windfire-restaurants-api}")
	private String apiClientId;
	@Value("${windfire.swagger.public:false}")
	private boolean swaggerPublic;
	@Value("${windfire.cors.allowed-origins}")
	private String allowedOrigins;
	@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
	private String issuerUri;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.cors(Customizer.withDefaults())
			.authorizeHttpRequests((authorize) -> {
				authorize.requestMatchers("/actuator/health").permitAll();
				if (swaggerPublic) {
					authorize.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
				}
				authorize
					.requestMatchers(HttpMethod.GET, "/restaurants/**").hasAnyRole("restaurants-reader", "restaurants-writer")
					.requestMatchers(HttpMethod.POST, "/restaurants/**").hasRole("restaurants-writer")
					.requestMatchers(HttpMethod.DELETE, "/restaurants/**").hasRole("restaurants-writer")
					.anyRequest().authenticated();
			})
			.oauth2ResourceServer((oauth2) -> oauth2.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
			.authenticationEntryPoint(authenticationEntryPoint)
		);
		return http.build();
	}

	/**
	 * Maps Keycloak client roles (resource_access.&lt;api-client-id&gt;.roles) to ROLE_ authorities.
	 */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(this::clientRoles);
		return converter;
	}

	private Collection<GrantedAuthority> clientRoles(Jwt jwt) {
		Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
		if (resourceAccess == null || !(resourceAccess.get(apiClientId) instanceof Map<?, ?> client)
				|| !(client.get("roles") instanceof Collection<?> roles)) {
			return Collections.emptyList();
		}
		return roles.stream()
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
	}

	/**
	 * OIDC discovery and JWKS calls to Keycloak trust only the "keycloak" SSL bundle (Windfire Keycloak Root CA);
	 * the rest of the JVM (MongoDB Atlas included) keeps the default trust.
	 */
	@Bean
	JwtDecoder jwtDecoder(RestTemplateBuilder restTemplateBuilder, SslBundles sslBundles) {
		RestTemplate keycloakRestTemplate = restTemplateBuilder.setSslBundle(sslBundles.getBundle(KEYCLOAK_SSL_BUNDLE)).build();
		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri)
				.restOperations(keycloakRestTemplate)
				.build();
		jwtDecoder.setJwtValidator(jwtValidator(issuerUri, apiClientId));
		return jwtDecoder;
	}

	/**
	 * Default issuer and timestamp checks, plus "aud" must contain the API client id.
	 */
	static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String clientId) {
		OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.contains(clientId));
		return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.stream(StringUtils.commaDelimitedListToStringArray(allowedOrigins))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}

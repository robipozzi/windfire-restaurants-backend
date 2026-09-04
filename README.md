# Windfire Restaurants application backend

## Table of Contents
- [Introduction](#introduction)
- [Requirements](#requirements)
- [Run](#run)
- [How the application works](#how-the-application-works)
    - [Implementing OAuth2 Authentication with Keycloak](#implementing-oauth2-authentication-with-keycloak)
    - [TLS configuration](#tls-configuration)
    - [Keycloak client trust (outbound TLS)](#keycloak-client-trust-outbound-tls)
    - [Test the application](#test-the-application)

## Introduction
This repository holds the code for Windfire Restaurants application backend services. It also provides scripts to facilitate
the build and run of the application.

Windfire Restaurants Backend implements and expose a REST service and is based and developed on Spring Boot.

This version implements OAuth2 authorization (using Keycloak as the Authorization Server) and uses TLS certificate to encrypt
the traffic.

The architecture of the application is depicted in the following picture.

![](img/oauth-with-Keycloak-TLS.png)

## Requirements
To build and run the application, following minimal software needs to be available on the machine:

* [Maven](https://maven.apache.org/install.html)
* Java 17: Any compliant JVM should work.

## Run
Once requirements are met, one can build and run the application with the following:
1. `mvn install`
2. `java -jar target/windfire-restaurants-backend-0.0.1-SNAPSHOT.jar`

A convenient script **[build.sh](build.sh)** is provided to build the application from source code.

Another script **[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)** is provided to run the application, once it has been built. Before running it for the first time, see [Keycloak client trust (outbound TLS)](#keycloak-client-trust-outbound-tls) for a one-time truststore setup step it now depends on.

## How the application works
The application implements a REST service, using Spring Boot construct, i.e. simply tagging a class as **@RestController**, as 
it can be seen in **RestaurantRESTService** here following

```
@RestController
public class RestaurantRESTService {
	private static final Logger logger = LoggerFactory.getLogger(RestaurantRESTService.class);
	@Autowired
	private RestaurantDao restaurantDao;

	/*
	 * REST service that returns all restaurants 
	 * GET is the only method allowed
	 */
	@GetMapping("/restaurants")
	public Iterable<Restaurant> findAllRestaurants() {
		logger.info("GET /restaurants");
		Iterable<Restaurant> restaurants = restaurantDao.findAll();
		return restaurants;
	}
	
	/*
	 * REST service that creates a new restaurant in database 
	 * POST is the only method allowed
	 */
	@PostMapping("/restaurants")
	public Restaurant addRestaurant(@RequestBody Restaurant restaurant) {
		logger.info("POST /restaurants");
		Restaurant newRestaurant = restaurantDao.save(restaurant);
		return newRestaurant;
	}

}
```

As said in the introduction, the code for this application is based on:
- **Maven**: here is the **[POM](pom.xml)** that defines project configuration; the library dependencies section is reported here below

```
<dependencies>
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-web</artifactId>
	</dependency>
	<!-- ############# START - ENABLE OAUTH2 SECURITY ############# -->
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
	</dependency>
	<!-- ############# END - ENABLE OAUTH2 SECURITY ############# -->
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-actuator</artifactId>
	</dependency>
	<dependency>
		<groupId>org.springframework.boot</groupId>	
		<artifactId>spring-boot-starter-test</artifactId>
		<scope>test</scope>
	</dependency>
</dependencies>
```

- **Spring Boot 3.2.1**: the usage of Spring Boot framework v3.2.1, with all its implicit dependencies, is declared in the same **[POM](pom.xml)**; as any Spring Boot application, it has a specific configuration file called **[application.properties](src/main/resources/application.properties)**

```
<parent>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-parent</artifactId>
	<version>3.2.1</version>
	<relativePath/> <!-- lookup parent from repository -->
</parent>
```

- **OAuth2 specific dependencies**: this dependency enables the application to behave as an OAuth2 Resource Server (which is a
specific role in OAuth2 protocol, as specified in *https://datatracker.ietf.org/doc/html/rfc6749#section-1.1*); OAuth2 behavior
is injected as Spring dependencies, as it can be seen in the **[POM](pom.xml)** dependencies section.

Every Spring Boot application needs to have a main class annotated as **@SpringBootApplication**; our application main class is 
**[WindfireApplication](src/main/java/com/rpozzi/windfire/application/WindfireApplication.java)**, whose code is reported here below for reference

```
@SpringBootApplication
@ComponentScan(basePackages = "com.rpozzi.windfire")
public class WindfireApplication {
	private static final Logger logger = LoggerFactory.getLogger(WindfireApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(WindfireApplication.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			logger.info("Application " + ctx.getId() + " started !!!");
		};
	}

}
```

### Implementing OAuth2 Authentication with Keycloak
Keycloak is an Open Source Identity and Access Management technology that allows to add authentication mechanisms to applications, securing them with minimum effort.

Keycloak provides user federation, strong authentication, user management, fine-grained authorization, and more.

To setup, configure and run a Keycloak server as an OAuth2 Authorization Server refer to the following article *[Building robust APIs: a comprehensive guide to OAuth2 and TLS integration in REST services — Part 2](https://medium.com/@robertopozzi/building-robust-apis-a-comprehensive-guide-to-oauth2-and-ssl-integration-in-rest-services-part-2-44213d2e3e62)*.

Once Keycloak is up and running, **spring.security.oauth2.resourceserver.jwt.issuer-uri** property needs to be set appropriately in **[application.properties](src/main/resources/application.properties)**, as follows:

```
### Security configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost:8443/realms/windfire-restaurants
```

This is really all is needed when using Spring Boot, everything is automatically wired up and the REST service is now protected under OAuth2 authentication; 

Let's do a small step forward and implement a simple error handling, returning a specific messages to a REST client, in case the client is not authenticated. To implement such behavior, a custom *AuthenticationEntryPoint* (it is an interface provided by Spring Security framework) needs to be implemented. The following code (implemented in  **[CustomAuthenticationEntryPoint](src/main/java/com/rpozzi/windfire/security/CustomAuthenticationEntryPoint.java)**) is quite straightforward, it just returns a JSON formatted response with an appropriate error message.

```
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        String errorType = "unauthorized";
        String errorMessage = "";
    	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        if (authException.getClass().getSimpleName().equals("InsufficientAuthenticationException")) {
			errorMessage = "No Bearer token provided";
		} else if (authException.getClass().getSimpleName().equals("InvalidBearerTokenException")) {
			errorMessage = "Invalid Bearer token provided";
		}
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorType);
        errorResponse.put("message", errorMessage);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

}
```

### TLS configuration
Enabling TLS on a Spring Boot REST service with a certificate signed by a Certificate Authority (CA) involves a few steps, refer to the following article *[Building robust APIs: a comprehensive guide to OAuth2 and TLS integration in REST services - Part 3](https://medium.com/@robertopozzi/building-robust-apis-a-comprehensive-guide-to-oauth2-and-ssl-integration-in-rest-services-part-2-44213d2e3e62)* for step by step instructions.

The server certificate is signed by a self-signed Windfire Root Certification Authority, generated once via the sibling **[windfire-security/ssl/createRootCA.sh](https://github.com/robipozzi/windfire-security/blob/main/ssl/createRootCA.sh)** script, which places the Root CA certificate in `$HOME/opt/windfire/ssl/truststore` and its private key in `$HOME/opt/windfire/ssl/keystore`.

A convenient script **[generate-ssl-cert.sh](ssl/generate-ssl-cert.sh)** is provided to generate the TLS certificate and private key needed to encrypt the traffic, signed by that Root CA. Running it prompts for:
- the target environment, either `localhost` or `raspberry` (each mapped to its own CN/SAN and its own output folders)
- the certificate file name, defaulting to `windfire-restaurants-backend.crt`
- the key file name, defaulting to `windfire-restaurants-backend.key`
- the Root CA certificate name, defaulting to `WindfireRootCA.crt` (its private key is expected alongside it, in the keystore folder, with the same base name and a `.key` extension)

Any required output folder that does not exist yet is created automatically as a pre-flight check. Generated files are stored as follows:

| Environment | Certificate folder | Key folder |
|---|---|---|
| localhost | `$HOME/opt/windfire/ssl/certs` | `$HOME/opt/windfire/ssl/keystore` |
| raspberry | `$HOME/opt/windfire/ssl/certs/raspberry` | `$HOME/opt/windfire/ssl/keystore/raspberry` |

Appropriate configuration parameters for TLS need to set in **[application.properties](src/main/resources/application.properties)**, as follows:

```
### SSL configuration
### PEM certificate configuration - certificate and key generated by ssl/generate-ssl-cert.sh,
### signed by the Windfire Root CA (see windfire-security/ssl/createRootCA.sh)
# The path to the certificate
server.ssl.certificate=file:${HOME_DIR}/opt/windfire/ssl/certs/windfire-restaurants-backend.crt
# The path to the certificate's private key
server.ssl.certificate-private-key=file:${HOME_DIR}/opt/windfire/ssl/keystore/windfire-restaurants-backend.key
```

### Keycloak client trust (outbound TLS)
The TLS configuration described just above is *inbound*: it lets clients trust this application's own
certificate. Since Keycloak was switched to HTTPS-only, serving `https://raspberry01:8443` with a
self-signed certificate, the application also needs *outbound* trust: when it fetches Keycloak's OIDC
metadata to validate incoming JWTs, the JVM needs to trust Keycloak's self-signed CA too. There is no
custom `RestTemplate`/`JwtDecoder`/`SSLContext` bean in the codebase, so this trust cannot be set as a
Spring property — it is supplied JVM-wide via `-Djavax.net.ssl.trustStore` /
`-Djavax.net.ssl.trustStorePassword` on the `java` invocation.

**Important:** because it is JVM-wide, this truststore covers every outbound TLS connection the
process makes, not just Keycloak — including the outbound MongoDB Atlas connection, whose certificate
is signed by a public CA. To avoid losing that trust, the truststore is not built from scratch: it is
seeded from the running JDK's own default `cacerts` (which already trusts the public CAs) and then
Keycloak's CA is imported into that copy.

A convenient script **[generate-keycloak-truststore.sh](ssl/generate-keycloak-truststore.sh)** is
provided to (re)build this truststore. It expects Keycloak's CA certificate to already exist at
`$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_CACERT_PEM` (it only rebuilds the truststore, it
does not create the CA certificate itself), then:
- copies the running JDK's default `cacerts` to
  `$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE`, replacing any existing truststore
  there
- imports Keycloak's CA certificate into that copy under alias `ca_keycloak`, using the password from
  `DEFAULT_KEYCLOAK_TRUSTSTORE_PWD` in **[setenv.sh](setenv.sh)** (`changeit` by default)
- verifies the merge worked, by confirming both a known public CA and the new `ca_keycloak` alias are
  listed in the resulting truststore

Run it once (and again whenever Keycloak's CA certificate changes) with:
```bash
cd ssl
./generate-keycloak-truststore.sh
```

This is a one-time/local setup step whose *output* (the `.jks` file) is not tracked in git — only the
script that produces it lives in the repository.

**[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)** now also prompts for the
truststore password, defaulting to `DEFAULT_KEYCLOAK_TRUSTSTORE_PWD` from **[setenv.sh](setenv.sh)**,
and passes it to the JVM via `-Djavax.net.ssl.trustStorePassword` alongside
`-Djavax.net.ssl.trustStore`.

### Test the application
Two scripts are provided to test the application
- **[requestKeycloakToken.sh](test/requestKeycloakToken.sh)** script allows to authenticate to Keycloak and get an Authorization Bearer token that can then be used to access the REST service.
- **[getRestaurants.sh](test/getRestaurants.sh)** script allows to call the REST service passing the Authorization Bearer token returned by Keycloak to ensure the client is authenticated.

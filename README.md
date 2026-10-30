# Windfire Restaurants application backend

## Table of Contents
- [Introduction](#introduction)
- [Requirements](#requirements)
- [Run](#run)
    - [Stop the application](#stop-the-application)
- [Deploy to Raspberry Pi](#deploy-to-raspberry-pi)
- [How the application works](#how-the-application-works)
    - [Layered architecture](#layered-architecture)
    - [REST API](#rest-api)
    - [API documentation with Swagger UI / OpenAPI](#api-documentation-with-swagger-ui--openapi)
    - [Data persistence with MongoDB](#data-persistence-with-mongodb)
    - [Implementing OAuth2 Authentication with Keycloak](#implementing-oauth2-authentication-with-keycloak)
    - [TLS configuration](#tls-configuration)
    - [Keycloak client trust (outbound TLS)](#keycloak-client-trust-outbound-tls)
    - [Test the application](#test-the-application)

## Introduction
This repository holds the code for Windfire Restaurants application backend services. It also provides scripts to facilitate
the build, run and deployment of the application.

Windfire Restaurants Backend implements and exposes a REST service, based and developed on Spring Boot, that manages
restaurant records stored in MongoDB.

This version implements OAuth2 authorization (using Keycloak as the Authorization Server), uses a TLS certificate to encrypt
the traffic, and documents itself through a Swagger UI / OpenAPI endpoint.

The architecture of the application is depicted in the following picture.

![](img/oauth-with-Keycloak-TLS.png)

## Requirements
To build and run the application, following minimal software needs to be available on the machine:

* [Maven](https://maven.apache.org/install.html)
* Java 17: Any compliant JVM should work.
* Access to a MongoDB instance (the application is configured out of the box to connect to a MongoDB Atlas cluster, see
[Data persistence with MongoDB](#data-persistence-with-mongodb)).

## Run
Once requirements are met, one can build and run the application with the following:
1. `mvn install`
2. `java -jar target/windfire-restaurants-backend-0.0.1-SNAPSHOT.jar`

A convenient script **[build.sh](build.sh)** is provided to build the application from source code.

Another script **[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)** is provided to run the application, once it has been built. It interactively prompts for the Keycloak server host/port, the allowed CORS origins (default `http://localhost:3000,https://localhost:3000`), the Keycloak client truststore password and the MongoDB password, exports them as environment variables consumed by **[application.properties](src/main/resources/application.properties)**, and starts the JVM in the background. Before running it for the first time, see [Keycloak client trust (outbound TLS)](#keycloak-client-trust-outbound-tls) for a one-time truststore setup step it depends on.

### Stop the application
**[stop-windfire-restaurants-backend.sh](stop-windfire-restaurants-backend.sh)** looks up the running `windfire-restaurants-backend` process, sends it a graceful `kill`, waits up to 10 seconds for it to shut down, and force-kills it (`kill -9`) if it hasn't stopped by then.

## Deploy to Raspberry Pi
The application can be fully built, packaged and deployed to a Raspberry Pi (`raspberry02`) as a supervised `systemd` service with a single command:

```bash
./deploy.sh
```

**[deploy.sh](deploy.sh)** orchestrates the whole chain:
1. Prompts for the target platform (currently only `1. Raspberry` is available).
2. Builds the application from source (`./build.sh 4`, i.e. `mvn clean install`).
3. Generates the TLS server certificate/key for the `raspberry` environment if not already present, via **[ssl/generate-ssl-cert.sh](ssl/generate-ssl-cert.sh)**.
4. Generates the Keycloak client truststore if it is missing or does not trust the current Windfire Keycloak Root CA, via **[ssl/generate-keycloak-truststore.sh](ssl/generate-keycloak-truststore.sh)** `--if-stale`.
5. Prompts once for the Keycloak host/port, the Keycloak truststore password and the MongoDB password, and exports them as environment variables.
6. Runs the Ansible playbook (with `-K`: sudo on `raspberry02` requires `pi`'s password, typed at the `BECOME password` prompt) **[deployment/raspberry/windfire-restaurants.yaml](deployment/raspberry/windfire-restaurants.yaml)**, which:
   - stops any previously running `windfire-restaurants-backend` systemd service;
   - copies the application jar, launch scripts, TLS certificate/key and Keycloak client truststore to the Pi;
   - templates the environment file and a `windfire-restaurants-backend.service` systemd unit (**[templates/windfire-restaurants-backend.service.j2](deployment/raspberry/templates/windfire-restaurants-backend.service.j2)**, **[templates/windfire-restaurants-backend.env.j2](deployment/raspberry/templates/windfire-restaurants-backend.env.j2)**);
   - encrypts the Keycloak truststore password and MongoDB password at rest on the Pi using `systemd-creds`, so they never sit in plaintext on disk, and wires them into the service via `LoadCredentialEncrypted`;
   - reloads `systemd`, starts and enables the service, and finally verifies the application is listening on port `8081`.

Deployment configuration (remote paths, users, file names, etc.) lives in **[deployment/raspberry/conf/config.yml](deployment/raspberry/conf/config.yml)**.

To remove the application from the Pi, run the companion uninstall playbook:
```bash
ansible-playbook -K deployment/raspberry/windfire-restaurants-uninstall.yaml
```
which stops and disables the systemd service, removes its unit file, and deletes the deployed application directory.

## How the application works

### Layered architecture
Each domain (e.g. restaurants) is organized in a small DAO / business-service layering, built on top of generic base classes:
- **[IDao](src/main/java/com/rpozzi/windfire/common/dao/IDao.java)** / **[ADao](src/main/java/com/rpozzi/windfire/common/dao/ADao.java)**: generic CRUD contract (`findAll`, `findById`, `save`, `deleteById`) that concrete DAOs implement.
- **[RestaurantDao](src/main/java/com/rpozzi/windfire/restaurants/entities/RestaurantDao.java)**: implements `ADao<Restaurant>` by delegating to **[IRestaurantRepository](src/main/java/com/rpozzi/windfire/restaurants/entities/IRestaurantRepository.java)**, a Spring Data `MongoRepository`.
- **[IBusinessService](src/main/java/com/rpozzi/windfire/common/service/IBusinessService.java)** / **[ABusinessService](src/main/java/com/rpozzi/windfire/common/service/ABusinessService.java)**: generic base business-service holding a reference to the DAO.
- **[RestaurantService](src/main/java/com/rpozzi/windfire/restaurants/service/RestaurantService.java)**: extends `ABusinessService<Restaurant>` and exposes the restaurant-specific business operations used by the REST controller.
- **[RestaurantRESTService](src/main/java/com/rpozzi/windfire/restaurants/service/RestaurantRESTService.java)**: the `@RestController` that exposes the operations over HTTP.

The **[Restaurant](src/main/java/com/rpozzi/windfire/restaurants/entities/Restaurant.java)** entity (with an embedded **[Address](src/main/java/com/rpozzi/windfire/restaurants/entities/Address.java)**) is persisted as a `restaurants` MongoDB document.

### REST API
The application implements a REST service, using Spring Boot construct, i.e. simply tagging a class as **@RestController**, as
it can be seen in **[RestaurantRESTService](src/main/java/com/rpozzi/windfire/restaurants/service/RestaurantRESTService.java)** here following (Swagger/OpenAPI annotations omitted for brevity, see [API documentation with Swagger UI / OpenAPI](#api-documentation-with-swagger-ui--openapi))

```java
@RestController
@Tag(name = "Restaurants", description = "Operations for managing restaurants")
public class RestaurantRESTService {
	@Autowired
	private RestaurantService restaurantService;

	@GetMapping("/restaurants")
	@CrossOrigin
	public Iterable<Restaurant> findAllRestaurants() {
		return restaurantService.findAllRestaurants();
	}

	@GetMapping("/restaurants/{id}")
	@CrossOrigin
	public Restaurant findRestaurantById(@PathVariable String id) {
		return restaurantService.findRestaurantById(id);
	}

	@PostMapping("/restaurants")
	@CrossOrigin
	public Restaurant addRestaurant(@RequestBody Restaurant restaurant) {
		return restaurantService.addRestaurant(restaurant);
	}

	@DeleteMapping("/restaurants/{id}")
	@CrossOrigin
	public void removeRestaurant(@PathVariable String id) {
		Restaurant restaurant = new Restaurant();
		restaurant.setId(id);
		restaurantService.removeRestaurant(restaurant);
	}

}
```

| Method | Path | Description |
|---|---|---|
| GET | `/restaurants` | Returns the list of all restaurants |
| GET | `/restaurants/{id}` | Returns a single restaurant identified by its ID |
| POST | `/restaurants` | Adds a new restaurant to the database |
| DELETE | `/restaurants/{id}` | Removes a restaurant from the database by its ID |

As said in the introduction, the code for this application is based on:
- **Maven**: here is the **[POM](pom.xml)** that defines project configuration; the library dependencies section is reported here below

```xml
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
		<artifactId>spring-boot-starter-data-mongodb</artifactId>
	</dependency>
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-actuator</artifactId>
	</dependency>
	<dependency>
		<groupId>org.springframework.boot</groupId>	
		<artifactId>spring-boot-starter-test</artifactId>
		<scope>test</scope>
	</dependency>
	<!-- ############# START - SWAGGER UI / OPENAPI ############# -->
	<dependency>
		<groupId>org.springdoc</groupId>
		<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
	</dependency>
	<!-- ############# END - SWAGGER UI / OPENAPI ############# -->
</dependencies>
```

- **Spring Boot 3.2.1**: the usage of Spring Boot framework v3.2.1, with all its implicit dependencies, is declared in the same **[POM](pom.xml)**; as any Spring Boot application, it has a specific configuration file called **[application.properties](src/main/resources/application.properties)**

```xml
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

```java
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

### API documentation with Swagger UI / OpenAPI
The REST API is self-documented via [springdoc-openapi](https://springdoc.org/), configured in **[OpenApiConfig](src/main/java/com/rpozzi/windfire/config/OpenApiConfig.java)** (API title/description and a `bearerAuth` HTTP Bearer/JWT security scheme) and exposed through the paths configured in **[application.properties](src/main/resources/application.properties)**:

```
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
```

Swagger UI and the OpenAPI document are **off by default**: `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` and `windfire.swagger.public` are `false` in **[application.properties](src/main/resources/application.properties)**, so the Raspberry deployment, which runs with no Spring profile, does not serve them. The `dev` profile in **[application-dev.properties](src/main/resources/application-dev.properties)** turns them on, and `run-windfire-restaurants-backend.sh` starts the application with `--spring.profiles.active=dev`.

With the `dev` profile, the interactive Swagger UI is available at `/swagger-ui.html` and the raw OpenAPI document at `/v3/api-docs`. Because `windfire.swagger.public=true`, **[SecurityConfig](src/main/java/com/rpozzi/windfire/security/SecurityConfig.java)** lets both paths through without an OAuth2 Bearer token, while every other endpoint (e.g. `/restaurants`) still requires authentication - Swagger UI lets you supply a Bearer token via the "Authorize" button to call those protected endpoints interactively.

### Data persistence with MongoDB
Restaurants are persisted in MongoDB using Spring Data MongoDB. **[Restaurant](src/main/java/com/rpozzi/windfire/restaurants/entities/Restaurant.java)** is annotated as a `@Document("restaurants")`, and **[IRestaurantRepository](src/main/java/com/rpozzi/windfire/restaurants/entities/IRestaurantRepository.java)** extends Spring Data's `MongoRepository<Restaurant, String>` to get CRUD operations for free; **[RestaurantDao](src/main/java/com/rpozzi/windfire/restaurants/entities/RestaurantDao.java)** wraps that repository behind the generic `IDao` contract described in [Layered architecture](#layered-architecture).

The MongoDB connection is configured in **[application.properties](src/main/resources/application.properties)**:

```
### MongoDb Data source configuration
spring.data.mongodb.uri=mongodb+srv://mongodb:${MONGODB_PASSWORD}@windfire.rxdd4.mongodb.net/restaurants
spring.data.mongodb.database=restaurants
```

The connection string points to a MongoDB Atlas cluster; the `${MONGODB_PASSWORD}` placeholder is resolved from the `MONGODB_PASSWORD` environment variable, which is supplied either interactively by **[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)** when running locally, or via the encrypted systemd credential when [deployed to Raspberry Pi](#deploy-to-raspberry-pi).

### Implementing OAuth2 Authentication with Keycloak
Keycloak is an Open Source Identity and Access Management technology that allows to add authentication mechanisms to applications, securing them with minimum effort.

Keycloak provides user federation, strong authentication, user management, fine-grained authorization, and more.

To setup, configure and run a Keycloak server as an OAuth2 Authorization Server refer to the following article *[Building robust APIs: a comprehensive guide to OAuth2 and TLS integration in REST services — Part 2](https://medium.com/@robertopozzi/building-robust-apis-a-comprehensive-guide-to-oauth2-and-ssl-integration-in-rest-services-part-2-44213d2e3e62)*.

Once Keycloak is up and running, **spring.security.oauth2.resourceserver.jwt.issuer-uri** property needs to be set appropriately in **[application.properties](src/main/resources/application.properties)**, as follows:

```
### Security configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://${KEYCLOAK_SERVER}:${KEYCLOAK_SERVER_PORT}/realms/windfire
```

Access control is centralized in **[SecurityConfig](src/main/java/com/rpozzi/windfire/security/SecurityConfig.java)**: it whitelists the Swagger UI/OpenAPI paths and requires an authenticated (JWT Bearer) request for everything else.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
    private CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults())
			.authenticationEntryPoint(authenticationEntryPoint)
		);
		return http.build();
	}

}
```

This is really all is needed when using Spring Boot, everything is automatically wired up and the REST service is now protected under OAuth2 authentication;

Let's do a small step forward and implement a simple error handling, returning a specific messages to a REST client, in case the client is not authenticated. To implement such behavior, a custom *AuthenticationEntryPoint* (it is an interface provided by Spring Security framework) needs to be implemented. The following code (implemented in  **[CustomAuthenticationEntryPoint](src/main/java/com/rpozzi/windfire/security/CustomAuthenticationEntryPoint.java)**) is quite straightforward, it just returns a JSON formatted response with an appropriate error message.

```java
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
Enabling TLS on a Spring Boot REST service with a certificate signed by a Certificate Authority (CA) involves a few steps, refer to the following article *[Building robust APIs: a comprehensive guide to OAuth2 and TLS integration in REST services - Part 3](https://medium.com/@robertopozzi/building-robust-apis-a-comprehensive-guide-to-oauth2-and-ssl-integration-in-rest-services-part-2-44213d2e3e62)* for step by step instructions.

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
certificate. Keycloak is HTTPS-only, serving `https://raspberry01:8443` with a certificate signed by the
dedicated **Windfire Keycloak Root CA** (set up by
[windfire-security/keycloak/security/setupTLS.sh](https://github.com/robipozzi/windfire-security/blob/main/keycloak/security/setupTLS.sh),
independent of the Windfire Root CA used for this application's own certificate), so the application also
needs *outbound* trust: when it fetches Keycloak's OIDC metadata and JWKS to validate incoming JWTs, the
JVM needs to trust the Windfire Keycloak Root CA too. There is no
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
`$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE`, i.e.
`~/opt/keycloak/ssl/truststore/WindfireKeycloakRootCA.crt`, the **Windfire Keycloak Root CA** created by
`windfire-security/keycloak/security/setupTLS.sh` (it only rebuilds the truststore, it does not create
the CA certificate itself), then:
- copies the running JDK's default `cacerts` to
  `$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE`, replacing any existing truststore
  there
- imports the Windfire Keycloak Root CA certificate into that copy under alias `windfire-keycloak-root`, using the password from
  `DEFAULT_KEYCLOAK_TRUSTSTORE_PWD` in **[setenv.sh](setenv.sh)** (`changeit` by default)
- verifies the merge worked, by confirming both a known public CA and the new `windfire-keycloak-root` alias are
  listed in the resulting truststore

Run it once (and again whenever Keycloak's CA certificate changes) with:
```bash
cd ssl
./generate-keycloak-truststore.sh
```

With `--if-stale` the script rebuilds the truststore only if it is missing or its `windfire-keycloak-root`
entry does not have the same SHA-256 fingerprint as the current Windfire Keycloak Root CA certificate.
Both **[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)** and
**[deploy.sh](deploy.sh)** run it this way before starting/deploying the application, so a new Keycloak
CA is picked up automatically: just make sure the new `WindfireKeycloakRootCA.crt` is in
`~/opt/keycloak/ssl/truststore`.

**Keycloak hostname:** Keycloak's server certificate only has `raspberry01` as Subject Alternative Name, so
the Keycloak host given to the run/deploy scripts (`KEYCLOAK_SERVER`) must be exactly `raspberry01`, not an
IP address or another name. It must also match the issuer Keycloak advertises
(`https://raspberry01:8443/realms/windfire`), since Spring checks the token `iss` claim against
`spring.security.oauth2.resourceserver.jwt.issuer-uri`.

This is a one-time/local setup step whose *output* (the `.jks` file) is not tracked in git — only the
script that produces it lives in the repository.

**[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)** prompts for the
truststore password, defaulting to `DEFAULT_KEYCLOAK_TRUSTSTORE_PWD` from **[setenv.sh](setenv.sh)**,
and passes it to the JVM via `-Djavax.net.ssl.trustStorePassword` alongside
`-Djavax.net.ssl.trustStore`. When [deployed to Raspberry Pi](#deploy-to-raspberry-pi), the same
truststore and password are instead delivered to the `systemd` service via an encrypted
`systemd-creds` credential.

### Test the application
Two scripts are provided to test the application
- **[requestKeycloakToken.sh](test/requestKeycloakToken.sh)** script allows to authenticate to Keycloak and get an Authorization Bearer token that can then be used to access the REST service.
- **[getRestaurants.sh](test/getRestaurants.sh)** script allows to call the REST service passing the Authorization Bearer token returned by Keycloak to ensure the client is authenticated.

Alternatively, once the application is running, the [Swagger UI](#api-documentation-with-swagger-ui--openapi) at `/swagger-ui.html` can be used to explore and call every endpoint interactively, supplying the Bearer token obtained from `requestKeycloakToken.sh` via its "Authorize" button.

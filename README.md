# Windfire Restaurants application backend

[![CI](https://github.com/robipozzi/windfire-restaurants-backend/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/robipozzi/windfire-restaurants-backend/actions/workflows/ci.yml)

## Table of Contents
- [Introduction](#introduction)
- [Requirements](#requirements)
- [Run](#run)
    - [Stop the application](#stop-the-application)
- [Deploy to Raspberry Pi](#deploy-to-raspberry-pi)
- [Continuous integration and dependency monitoring](#continuous-integration-and-dependency-monitoring)
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

Another script **[start-windfire-restaurants-backend.sh](start-windfire-restaurants-backend.sh)** is provided to run the application, once it has been built. It interactively prompts for the Keycloak server host/port, the allowed CORS origins (default `https://localhost`) and the MongoDB **dev** password (user `windfire-dev-app`), exports them as environment variables consumed by **[application.properties](src/main/resources/application.properties)**, and starts the JVM in the background. It stops early if the Windfire Keycloak Root CA certificate is missing, see [Keycloak client trust (outbound TLS)](#keycloak-client-trust-outbound-tls).

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
4. Checks that the Windfire Keycloak Root CA certificate (`~/opt/keycloak/ssl/truststore/WindfireKeycloakRootCA.crt`) exists, and stops otherwise.
5. Prompts once for the Keycloak host/port and the MongoDB **prod** password (user `windfire-prod-app`), and exports them as environment variables.
6. Runs the Ansible playbook (with `-K`: sudo on `raspberry02` requires `pi`'s password, typed at the `BECOME password` prompt) **[deployment/raspberry/windfire-restaurants.yaml](deployment/raspberry/windfire-restaurants.yaml)**, which:
   - stops any previously running `windfire-restaurants-backend` systemd service;
   - copies the application jar, launch scripts, TLS certificate/key and the Windfire Keycloak Root CA certificate (`WindfireKeycloakRootCA.crt`, mode `0644`) to the Pi, and removes any legacy `keycloak.truststore.jks` left there;
   - templates the environment file (Keycloak host/port, CORS origins, and the prod `MONGODB_HOST`/`MONGODB_USER` from `config.yml`) and a `windfire-restaurants-backend.service` systemd unit (**[templates/windfire-restaurants-backend.service.j2](deployment/raspberry/templates/windfire-restaurants-backend.service.j2)**, **[templates/windfire-restaurants-backend.env.j2](deployment/raspberry/templates/windfire-restaurants-backend.env.j2)**);
   - encrypts the MongoDB password at rest on the Pi using `systemd-creds`, so it never sits in plaintext on disk, and wires it into the service via `LoadCredentialEncrypted`;
   - reloads `systemd`, starts and enables the service, and finally verifies the application is listening on port `8081`.

Deployment configuration (remote paths, users, file names, etc.) lives in **[deployment/raspberry/conf/config.yml](deployment/raspberry/conf/config.yml)**.

To remove the application from the Pi, run the companion uninstall playbook:
```bash
ansible-playbook -K deployment/raspberry/windfire-restaurants-uninstall.yaml
```
which stops and disables the systemd service, removes its unit file, and deletes the deployed application directory.

## Continuous integration and dependency monitoring
**[ci.yml](.github/workflows/ci.yml)** runs `./mvnw -B verify` on Java 17 for every pull request and every push to `main`. The badge at the top of this page shows the result for `main`; because the repository is private, it only renders for viewers who have access to it.

**[dependabot.yml](.github/dependabot.yml)** checks Maven dependencies and GitHub Actions weekly. Minor and patch updates are grouped into one pull request per ecosystem, with at most 5 open pull requests per ecosystem.

To read Dependabot alerts, open the repository's **Security > Dependabot** tab, or run:
```bash
gh api repos/robipozzi/windfire-restaurants-backend/dependabot/alerts
```

**An empty alert list does not mean the dependencies are free of known vulnerabilities:**
- Alerts only cover the direct entries of **[pom.xml](pom.xml)**. GitHub does not see the versions managed by the Spring Boot parent, or any transitive dependency.
- `./mvnw -B verify` builds and tests the application, but runs no vulnerability audit.

The workflow also defines a dependency review job (`actions/dependency-review-action`, failing on `high` severity). It is skipped while the repository is private, because GitHub only offers dependency review on private repositories with Advanced Security.

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
		<artifactId>spring-boot-starter-webmvc</artifactId>
	</dependency>
	<!-- ############# START - ENABLE OAUTH2 SECURITY ############# -->
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
	</dependency>
	<!-- RestTemplateBuilder, used by the JwtDecoder to call Keycloak with the "keycloak" SSL bundle -->
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-restclient</artifactId>
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
		<artifactId>spring-boot-starter-webmvc-test</artifactId>
		<scope>test</scope>
	</dependency>
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-security-oauth2-resource-server-test</artifactId>
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

- **Spring Boot 4.1.2**: the usage of Spring Boot framework v4.1.2, with all its implicit dependencies, is declared in the same **[POM](pom.xml)**; as any Spring Boot application, it has a specific configuration file called **[application.properties](src/main/resources/application.properties)**

```xml
<parent>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-parent</artifactId>
	<version>4.1.2</version>
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

Swagger UI and the OpenAPI document are **off by default**: `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` and `windfire.swagger.public` are `false` in **[application.properties](src/main/resources/application.properties)**, so the Raspberry deployment, which runs with no Spring profile, does not serve them. The `dev` profile in **[application-dev.properties](src/main/resources/application-dev.properties)** turns them on, and `start-windfire-restaurants-backend.sh` starts the application with `--spring.profiles.active=dev`.

With the `dev` profile, the interactive Swagger UI is available at `/swagger-ui.html` and the raw OpenAPI document at `/v3/api-docs`. Because `windfire.swagger.public=true`, **[SecurityConfig](src/main/java/com/rpozzi/windfire/security/SecurityConfig.java)** lets both paths through without an OAuth2 Bearer token, while every other endpoint (e.g. `/restaurants`) still requires authentication - Swagger UI lets you supply a Bearer token via the "Authorize" button to call those protected endpoints interactively.

### Data persistence with MongoDB
Restaurants are persisted in MongoDB using Spring Data MongoDB. **[Restaurant](src/main/java/com/rpozzi/windfire/restaurants/entities/Restaurant.java)** is annotated as a `@Document("restaurants")`, and **[IRestaurantRepository](src/main/java/com/rpozzi/windfire/restaurants/entities/IRestaurantRepository.java)** extends Spring Data's `MongoRepository<Restaurant, String>` to get CRUD operations for free; **[RestaurantDao](src/main/java/com/rpozzi/windfire/restaurants/entities/RestaurantDao.java)** wraps that repository behind the generic `IDao` contract described in [Layered architecture](#layered-architecture).

Dev and prod use **two separate MongoDB Atlas clusters**, each with its own database user. The database (`restaurants`) and the collection (`restaurants`) are the same in both:

| Environment | Started by | Atlas host | User | Where host/user come from |
|---|---|---|---|---|
| dev | [start-windfire-restaurants-backend.sh](start-windfire-restaurants-backend.sh) (`dev` profile) | `windfire-dev.dx6zesz.mongodb.net` (project `windfire-dev`) | `windfire-dev-app` | [application-dev.properties](src/main/resources/application-dev.properties) |
| prod | [deploy.sh](deploy.sh) → systemd on `raspberry02` (no profile) | `windfire.rxdd4.mongodb.net` | `windfire-prod-app` | `mongodb_host` / `mongodb_user` in [config.yml](deployment/raspberry/conf/config.yml), templated as `MONGODB_HOST` / `MONGODB_USER` into the service environment file |

The connection string is built in **[application.properties](src/main/resources/application.properties)** from those parts:

```
windfire.mongodb.host=${MONGODB_HOST}
windfire.mongodb.user=${MONGODB_USER}
spring.mongodb.database=restaurants
spring.mongodb.uri=mongodb+srv://${windfire.mongodb.user}:${MONGODB_PASSWORD}@${windfire.mongodb.host}/${spring.mongodb.database}
```

The same jar is promoted from dev to prod unchanged: only the host, user and password differ.

- **Host and user** have no default in `application.properties`. A run that has neither the `dev` profile nor the
  `MONGODB_HOST`/`MONGODB_USER` variables cannot resolve them, so it fails instead of silently connecting to the
  other environment's database.
- **The password** (`MONGODB_PASSWORD`) is never committed. Locally, [start-windfire-restaurants-backend.sh](start-windfire-restaurants-backend.sh) prompts for the **dev** password. On Raspberry, [deploy.sh](deploy.sh) prompts for the **prod** password, and the playbook stores it as an encrypted systemd credential (see [Deploy to Raspberry Pi](#deploy-to-raspberry-pi)).
- The `dev` profile sets only `windfire.mongodb.host`/`user`, never `spring.mongodb.uri`. The context tests run
  with profiles `{test, dev}` and must keep the localhost URI of `application-test.properties`, which
  `MongoDevProfileTest` checks.

The dev cluster holds a random sample of production restaurants, copied once when it was created. It is not kept in sync with prod.

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
needs *outbound* trust: when it fetches Keycloak's OIDC metadata and JWKS to validate incoming JWTs, it has
to trust the Windfire Keycloak Root CA.

This trust is scoped to Keycloak only, via a Spring Boot PEM SSL bundle named `keycloak`, declared in
**[application.properties](src/main/resources/application.properties)**:

```
spring.ssl.bundle.pem.keycloak.truststore.certificate=file:${HOME_DIR}/opt/keycloak/ssl/truststore/WindfireKeycloakRootCA.crt
```

The `JwtDecoder` bean in **[SecurityConfig.java](src/main/java/com/rpozzi/windfire/security/SecurityConfig.java)**
builds its `RestTemplate` with `RestTemplateBuilder.sslBundle(...)` on that bundle and passes it to
`NimbusJwtDecoder.withIssuerLocation(...)`, so both the OIDC discovery call and the JWKS fetch trust the
Windfire Keycloak Root CA. Nothing else uses the bundle: the JVM default trust (the JDK `cacerts`) is left
untouched, so MongoDB Atlas, whose certificate is signed by a public CA, and any other outbound TLS
connection keep working as before. **[MongoTlsTrustTest](src/test/java/com/rpozzi/windfire/config/MongoTlsTrustTest.java)**
checks that the Mongo client never gets a custom `SSLContext`.

There is no JKS truststore, no truststore password and no `-Djavax.net.ssl.trustStore*` JVM flag. The only
prerequisite is the CA certificate itself at `~/opt/keycloak/ssl/truststore/WindfireKeycloakRootCA.crt`
(`$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE` in **[setenv.sh](setenv.sh)**),
created by `windfire-security/keycloak/security/setupTLS.sh`. Both
**[start-windfire-restaurants-backend.sh](start-windfire-restaurants-backend.sh)** and **[deploy.sh](deploy.sh)**
stop early if it is missing. When Keycloak's CA changes, replace the file and restart (or redeploy) the
application. When [deployed to Raspberry Pi](#deploy-to-raspberry-pi), the playbook copies the certificate
(mode `0644`, it is public) to the same path under `/home/pi`, where `HOME_DIR` points.

**Keycloak hostname:** Keycloak's server certificate only has `raspberry01` as Subject Alternative Name, so
the Keycloak host given to the run/deploy scripts (`KEYCLOAK_SERVER`) must be exactly `raspberry01`, not an
IP address or another name. It must also match the issuer Keycloak advertises
(`https://raspberry01:8443/realms/windfire`), since Spring checks the token `iss` claim against
`spring.security.oauth2.resourceserver.jwt.issuer-uri`.

### Test the application
**[run-tests.sh](run-tests.sh)** runs the whole test suite. For every test it prints what the test is, what it
does and whether it passed, then a summary per area with pass/fail/skip counts and the list of failures.

```
./run-tests.sh                  # asks for the environment (dev/test/prod), then JUnit + live tests on it
./run-tests.sh --only Security  # only the JUnit classes whose name contains "Security"
./run-tests.sh --e2e            # JUnit tests, then the live end-to-end tests (asks for the environment)
./run-tests.sh --e2e-only       # only the live end-to-end tests (asks for the environment)
./run-tests.sh --env dev        # JUnit tests, then the live tests on a local backend (before deploying)
./run-tests.sh --env prod       # JUnit tests, then the live tests on the Raspberry deployment
```

`--env` chooses the environment the live tests run against (it implies `--e2e`). Without it the script asks;
without a terminal (CI, pipes) a run with no options runs the JUnit tests only, and `--e2e` targets `prod`.

| | `dev` | `test` | `prod` |
|---|---|---|---|
| Backend | `https://localhost:8081`, `dev` profile, **started and stopped by the script** | not configured yet: live tests skipped (set `WF_BASE_URL` to test a server) | `https://raspberry02:8081` (deployed, no Spring profile) |
| CORS origin tested | `https://localhost` | `WF_ALLOWED_ORIGIN` | `https://raspberry01:9443` |
| Swagger | API docs and Swagger UI must be public | must require a token | must require a token |

On `dev`, if a backend already answers on `https://localhost:8081` it is used and left running. Otherwise the script
packages `target/windfire-restaurants-backend-0.0.1-SNAPSHOT.jar` if it is missing or older than the sources,
starts it in background with the same settings as
[start-windfire-restaurants-backend.sh](start-windfire-restaurants-backend.sh) (log in
`target/test-results/backend.log`), waits until it answers and, when the live tests end (or fail, or are
interrupted), stops **only the process it started**. Its settings:
- `WF_MONGODB_PASSWORD` - MongoDB dev password, prompted if unset and a terminal is attached
- `WF_KEYCLOAK_HOST` / `WF_KEYCLOAK_PORT` - Keycloak the backend trusts (default `raspberry01:8443`)
- `WF_BACKEND_START_TIMEOUT` - seconds to wait for it to answer (default 120)
- `--no-start` never starts it (fails if none is running); `--keep-backend` leaves it running after the tests.

Keycloak and the Windfire Root CA are the same for every environment. If the backend cannot be reached, the
first test fails with the reason and the remaining live tests are skipped.

Exit code is 0 when everything passed, 1 when a test failed and 2 when the tests could not run (missing tool,
build error). The per-test results are written to `target/test-results/results.tsv` and the full Maven output to
`target/test-results/maven.log`.

The JUnit tests (`mvn test` runs them too) are organised in three layers:
- **unit** - plain JUnit/Mockito: Keycloak role mapping, JWT validation (issuer, audience, expiry), the 401 body,
  field constraints of `Restaurant`/`Address`, the business service and the DAO (with a mocked MongoDB repository).
- **web** - `@WebMvcTest` with the real `SecurityConfig`: authentication, reader/writer authorization, CORS,
  Swagger access, REST status codes and request validation.
- **context** - the full application with only Keycloak and MongoDB mocked: wiring, `/actuator/health`, and
  Swagger with and without the `dev` profile.

Each test declares its description with `@DisplayName` and `@Does` (see
[testsupport](src/test/java/com/rpozzi/windfire/testsupport)).

The live end-to-end tests in **[e2e-tests.sh](test/e2e-tests.sh)** call the running backend, Keycloak and, through
the backend, MongoDB:
- **TLS & health** - the server certificate is trusted by the Windfire Root CA; `/actuator/health` is public and `UP`
  (its status includes the backend's MongoDB health check).
- **Keycloak & authentication** - missing or garbage tokens get 401; Keycloak rejects a wrong password; reader and
  writer logins work, and their tokens carry what the API checks (`iss`, the `windfire-restaurants-api` audience,
  the client role). When a claim is missing, the failure says what to fix in Keycloak.
- **Authorization** - reader and writer can list, a reader cannot create, a user without API roles gets 403.
  The Keycloak users are `windfire-reader` (reader), `windfire` (writer) and `windfire-norole` (no API role);
  override them with `WF_READER_USER`, `WF_WRITER_USER` and `WF_NOROLE_USER`.
- **MongoDB round trip** - a restaurant with every field filled is created, read back field by field and found in the
  list; a client-supplied `id` cannot overwrite it; an invalid body is rejected and not stored; a reader cannot delete
  it; the writer deletes it and it is gone (404, not in the list); unknown ids answer 404 for GET and DELETE.
- **CORS & Swagger** - allowed/unknown origin preflights; Swagger private on `prod`, public on `dev`.

They are configured with `WF_*` environment variables (see the header of the script), whose defaults depend on the
chosen environment; any of them (e.g. `WF_BASE_URL`) can be set to override it. Passwords and the client secret that
are not set are prompted for.

**Keycloak client for the e2e tests.**
The tests log in with the Resource Owner Password grant (`grant_type=password`). They use their own Keycloak client,
**`windfire-e2e`**, and not `windfire`. `windfire` is the React SPA's public client: it uses Authorization Code + PKCE
and has **Direct access grants Off**, so password logins on it are refused with `unauthorized_client`. Turning that
setting back on would allow password logins on a public browser client again. Create `windfire-e2e` in realm
`windfire`:

| Setting | Value |
|---|---|
| Client type | OpenID Connect |
| Client authentication | **On** (confidential: knowing the client id is not enough to use the password grant) |
| Standard flow / Implicit / Service accounts / Device | Off |
| Direct access grants | **On** |
| Redirect URIs / Web origins | empty |

- **Audience**: *Client scopes → windfire-e2e-dedicated → Add mapper → By configuration → Audience*, with Included
  Client Audience = `windfire-restaurants-api` and Add to access token = On. If `windfire` gets the audience from a
  shared client scope, assign that scope instead. Without it the tests fail with "aud lacks windfire-restaurants-api".
- **Roles**: nothing to add. `resource_access.windfire-restaurants-api.roles` comes from the users' role mappings,
  through the default `roles` client scope.
- **Secret**: copy it from the *Credentials* tab and pass it as **`WF_CLIENT_SECRET`**, or type it when prompted.
  Never store it in the repository.

To use another client, set `WF_CLIENT_ID` (and `WF_CLIENT_SECRET` if it is confidential).

Without credentials the Keycloak, authorization and MongoDB tests cannot run: they are reported as skipped and
the summary warns that they were **not verified**. Add **`--strict`** to make them fail instead, so that a PASSED
run really means everything was checked (recommended before a deployment):

```
./run-tests.sh --env dev --strict
```

**The writer tests create and then delete restaurants named `E2E-<timestamp>…` in the MongoDB database of the
environment under test**; if a run is interrupted, whatever it created is deleted on exit. Dev and prod use
[separate clusters](#data-persistence-with-mongodb): `--env dev` writes only to the dev cluster, while a prod run
writes to (and cleans up in) the production data. Each run also makes
one deliberate wrong-password login for the reader user, which Keycloak records as a failed login.

Once the application is running, the [Swagger UI](#api-documentation-with-swagger-ui--openapi) at `/swagger-ui.html` can also be used to explore and call every endpoint interactively, supplying a Bearer token obtained from Keycloak via its "Authorize" button.

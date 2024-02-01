# Windfire Restaurants application backend

### Table of Contents
- [Introduction](#introduction)
- [Requirements](#requirements)
- [Run](#run)
- [Run](#run)
    
### Introduction
This repository holds the code for Windfire Restaurants application backend services. It also provides scripts to facilitate
the build and run of the application.

Windfire Restaurants Backend implements and expose a REST service and is based and developed with Spring Boot.

This version implements OAuth2 authorization (using Keycloak as the Authorization Server) and uses SSL certificate to encrypt
the traffic.


### Requirements
To build and run the application, following minimal software needs to be available on the machine:

* [Maven](https://maven.apache.org/install.html)
* Java 17: Any compliant JVM should work.


### Run
Once requirements
To build and run the application one can :
1. `mvn install`
2. `java -jar target/windfire-restaurants-backend-0.0.1-SNAPSHOT.jar`

A convenient script **build.sh** is provided to build the application from source code.

Another script **run-windfire-restaurants-backend.sh** is provided to run the application, once it has been built.

### How the application works
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

- **OAuth2 specific dependencies**: t

#### OAuth2 configuration
[TODO]

#### SSL configuration
[TODO]

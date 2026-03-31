package com.rpozzi.windfire.restaurants.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.rpozzi.windfire.restaurants.entities.Restaurant;

@RestController
@Tag(name = "Restaurants", description = "Operations for managing restaurants")
public class RestaurantRESTService {
	private static final Logger logger = LoggerFactory.getLogger(RestaurantRESTService.class);
	@Autowired
	private RestaurantService restaurantService;

	@Operation(summary = "Get all restaurants", description = "Returns the list of all restaurants")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Successful operation",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = Restaurant.class)))),
		@ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid Bearer token", content = @Content)
	})
	@GetMapping("/restaurants")
	@CrossOrigin
	public Iterable<Restaurant> findAllRestaurants() {
		logger.info("GET /restaurants");
		Iterable<Restaurant> restaurants = restaurantService.findAllRestaurants();
		return restaurants;
	}

	@Operation(summary = "Get restaurant by ID", description = "Returns a single restaurant identified by its ID")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Successful operation",
				content = @Content(schema = @Schema(implementation = Restaurant.class))),
		@ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid Bearer token", content = @Content),
		@ApiResponse(responseCode = "404", description = "Restaurant not found", content = @Content)
	})
	@GetMapping("/restaurants/{id}")
	@CrossOrigin
	public Restaurant findRestaurantById(
			@Parameter(description = "ID of the restaurant to retrieve", required = true) @PathVariable String id) {
		logger.info("GET /restaurants/" + id);
		Restaurant restaurant = restaurantService.findRestaurantById(id);
		return restaurant;
	}

	@Operation(summary = "Create a new restaurant", description = "Adds a new restaurant to the database")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Restaurant created successfully",
				content = @Content(schema = @Schema(implementation = Restaurant.class))),
		@ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
		@ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid Bearer token", content = @Content)
	})
	@PostMapping("/restaurants")
	@CrossOrigin
	public Restaurant addRestaurant(
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "Restaurant object to be created", required = true,
					content = @Content(schema = @Schema(implementation = Restaurant.class)))
			@RequestBody Restaurant restaurant) {
		logger.info("POST /restaurants");
		Restaurant newRestaurant = restaurantService.addRestaurant(restaurant);
		return newRestaurant;
	}

	@Operation(summary = "Delete a restaurant", description = "Removes a restaurant from the database by its ID")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Restaurant deleted successfully", content = @Content),
		@ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid Bearer token", content = @Content),
		@ApiResponse(responseCode = "404", description = "Restaurant not found", content = @Content)
	})
	@DeleteMapping("/restaurants/{id}")
	@CrossOrigin
	public void removeRestaurant(
			@Parameter(description = "ID of the restaurant to delete", required = true) @PathVariable String id) {
		logger.info("DELETE /restaurants/" + id);
		Restaurant restaurant = new Restaurant();
		restaurant.setId(id);
		restaurantService.removeRestaurant(restaurant);
	}

}
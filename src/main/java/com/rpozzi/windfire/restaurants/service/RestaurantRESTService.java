package com.rpozzi.windfire.restaurants.service;

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
import com.rpozzi.windfire.restaurants.entities.RestaurantDao;

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
	@CrossOrigin
	public Iterable<Restaurant> findAllRestaurants() {
		logger.info("GET /restaurants");
		Iterable<Restaurant> restaurants = restaurantDao.findAll();
		return restaurants;
	}
	
	/*
	 * REST service that queries restaurant by id 
	 * GET is the only method allowed
	 */
	@GetMapping("/restaurants/{id}")
	@CrossOrigin
	public Restaurant findRestaurantById(@PathVariable String id) {
		logger.info("GET /restaurants/" + id);
		Restaurant restaurant = restaurantDao.findById(id);
		return restaurant;
	}
	
	/*
	 * REST service that creates a new restaurant in database 
	 * POST is the only method allowed
	 */
	@PostMapping("/restaurants")
	@CrossOrigin
	public Restaurant addRestaurant(@RequestBody Restaurant restaurant) {
		logger.info("POST /restaurants");
		Restaurant newRestaurant = restaurantDao.save(restaurant);
		return newRestaurant;
	}
	
	/*
	 * REST service that deletes restaurant from database 
	 * DELETE is the only method allowed
	 */
	@DeleteMapping("/restaurants/{id}")
	@CrossOrigin
	public void removeRestaurant(@PathVariable String id) {
		logger.info("DELETE /restaurants/" + id);
		Restaurant restaurant = new Restaurant();
		restaurant.setId(id);
		restaurantDao.deleteById(id);
	}

}
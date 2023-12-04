package com.rpozzi.windfire.restaurants.service;

import java.util.Iterator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.rpozzi.windfire.common.dao.IDao;
import com.rpozzi.windfire.common.service.ABusinessService;
import com.rpozzi.windfire.restaurants.entities.Restaurant;

@RestController
public class RestaurantService extends ABusinessService<Restaurant>{
	
	public RestaurantService(IDao<Restaurant> dao) {
		super(dao);
	}
	
	/*
	 * REST service that queries all restaurants 
	 * GET is the only method allowed
	 */
	@GetMapping("/restaurants")
	public Iterable<Restaurant> findAllRestaurants() {
		logger.info("GET /restaurants");
		logger.debug("Executing " + this.getClass().getName() + ".findAllRestaurants()");
		Iterable<Restaurant> restaurants = dao.findAll();
		for (Iterator<Restaurant> iterator = restaurants.iterator(); iterator.hasNext();) {
			Restaurant restaurant = iterator.next();
			logger.info(restaurant.getId() + " - " + restaurant.getName());
		}
		return restaurants;
	}

	/*
	 * REST service that creates a new restaurant in database table 
	 * POST is the only method allowed
	 * REST client invoking the service needs 
	 * - Header
	 * Content-Type : application/json 
	 * - Body :
	 * {
	 * 	"name": "<NAME_OF_RESTAURANTS>",
	 * 	"address": 
	 * 		{
	 * 			"zipCode": "<ZIPCODE>",
	 * 			"city": "<CITY>",
	 * 			"street": "<ADDRESS>"
	 * 		},
	 * 	"cuisine": "<TYPE_OF_CUISINE>"
	 * }
	 */
	@PostMapping("/restaurants")
	public Restaurant addRestaurant(@RequestBody Restaurant restaurant) {
		logger.info("POST /restaurants");
		logger.debug("Executing " + this.getClass().getName() + ".addRestaurant()");
		logger.info("Creating restaurant : " + restaurant.getId() + " - " + restaurant.getName());
		Restaurant newRestaurant = dao.save(restaurant);
		logger.debug("Restaurant added !!!");
		logger.debug("   id = " + newRestaurant.getId());
		logger.debug("   name = " + newRestaurant.getName());
		logger.debug("   address = " + newRestaurant.getAddress().getStreet() + ", "
				+ newRestaurant.getAddress().getZipCode() + " " + newRestaurant.getAddress().getCity());
		return newRestaurant;
	}
}
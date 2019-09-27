package com.rp.windfire.restaurants.service;

import java.util.Iterator;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import com.rp.windfire.common.dao.IDao;
import com.rp.windfire.common.service.ABusinessService;
import com.rp.windfire.config.IApplicationConfiguration;
import com.rp.windfire.restaurants.entities.Restaurant;

@RestController
public class RestaurantService extends ABusinessService<Restaurant>{
	
	public RestaurantService(IDao<Restaurant> dao, IApplicationConfiguration config) {
		super(dao, config);
	}
	
	/*
	 * REST service that queries all restaurants GET is the only method allowed
	 */
	@RequestMapping(path = "/restaurants", method = RequestMethod.GET)
	public Iterable<Restaurant> findAllRestaurants() {
		logger.info("GET /restaurants");
		Iterable<Restaurant> restaurants = dao.findAll();
		for (Iterator<Restaurant> iterator = restaurants.iterator(); iterator.hasNext();) {
			Restaurant restaurant = iterator.next();
			logger.info(restaurant.getId() + " - " + restaurant.getName());
		}
		return restaurants;
	}

	/*
	 * REST service that create a new restaurant in database table POST is the only
	 * method allowed, a REST client invoking the service needs - Header
	 * Content-Type : application/json - body :
	 * {"restaurantName":"<restaurant name>"}
	 */
	@RequestMapping(path = "/restaurants", method = RequestMethod.POST)
	public Restaurant addRestaurant(@RequestBody Restaurant restaurant) {
		logger.info("POST /restaurants");
		logger.info("Creating restaurant : " + restaurant.getId() + " - " + restaurant.getName());
		Restaurant newRestaurant = dao.save(restaurant);
		return newRestaurant;
	}

}
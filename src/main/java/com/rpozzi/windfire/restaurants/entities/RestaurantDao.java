package com.rpozzi.windfire.restaurants.entities;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.rpozzi.windfire.restaurants.service.RestaurantRESTService;

@Component
public class RestaurantDao {
	private static final Logger logger = LoggerFactory.getLogger(RestaurantRESTService.class);

	public Iterable<Restaurant> findAll() {
		logger.debug("Executing " + this.getClass().getName() + ".findAll()");
		Iterable<Restaurant> restaurants = __findAll_fake();
		return restaurants;
	}

	public Restaurant save(Restaurant restaurant) {
		logger.debug("Executing " + this.getClass().getName() + ".save(restaurant)");
		restaurant.setId("3");
		Restaurant newRestaurant = restaurant;
		logger.debug("   id = " + newRestaurant.getId());
		logger.debug("   name = " + newRestaurant.getName());
		logger.debug("   address = " + newRestaurant.getAddress().getStreet() + ", "
				+ newRestaurant.getAddress().getZipCode() + " " + newRestaurant.getAddress().getCity());
		return newRestaurant;
	}
	
	private Iterable<Restaurant> __findAll_fake() {
		logger.warn("WARNING : returning a fake list of restaurants");
		logger.debug("Executing " + this.getClass().getName() + ".__findAll_fake()");
		List<Restaurant> restaurants = new ArrayList<>();
		// Create Restaurant 1
		Restaurant restaurant1 = new Restaurant();
		restaurant1.setId("1");
		restaurant1.setName("Il Vecchio Tram");
		restaurant1.setPhone("0321778914");
		restaurant1.setMobile("3489008762");
		restaurant1.setEmail("ilvecchiotram@gmail.com");
		restaurant1.setWebsite("www.ilvecchiotram.it");
		// Create Restaurant 2
		Restaurant restaurant2 = new Restaurant();
		restaurant2.setId("2");
		restaurant2.setName("Battipalo");
		restaurant2.setPhone("0321713829");
		restaurant2.setMobile("3457708391");
		restaurant2.setEmail("battipalo@gmail.com");
		restaurant2.setWebsite("www.battipalo.com");
		// Create Restaurant List
		restaurants.add(restaurant1);
		restaurants.add(restaurant2);
		return restaurants;
	}
}
package com.rpozzi.windfire.restaurants.entities;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RestaurantDao {
	private static final Logger logger = LoggerFactory.getLogger(RestaurantDao.class);

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
		restaurant1.setName("La Taverna del Pittore");
		// START - Set Address 1
		Address address1 = new Address("28041", "Arona", "Piazza del Popolo, 39", "NO", "Piemonte", "Italia");
		restaurant1.setAddress(address1);
		// END - Set Address 1
		restaurant1.setPhone("0322243366");
		restaurant1.setMobile("");
		restaurant1.setEmail("tavernadelpittore.arona@gmail.com");
		restaurant1.setWebsite("www.tavernadelpittore.com");
		// Create Restaurant 2
		Restaurant restaurant2 = new Restaurant();
		restaurant2.setId("2");
		restaurant2.setName("Battipalo");
		// START - Set Address 2
		Address address2 = new Address("28041", "Lesa", "Viale Vittorio Veneto, 2", "NO", "Piemonte", "Italia");
		restaurant2.setAddress(address2);
		// END - Set Address 1
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
package com.rpozzi.windfire.restaurants.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.rpozzi.windfire.common.dao.ADao;

@Component
public class RestaurantDao extends ADao<Restaurant> {
	@Autowired
	private IRestaurantRepository restaurantMongoDbRepo;

	@Override
	public Iterable<Restaurant> findAll() {
		logger.debug("Executing " + this.getClass().getName() + ".findAll()");
		Iterable<Restaurant> restaurants = restaurantMongoDbRepo.findAll();
		// Iterable<Restaurant> restaurants = __findAll_fake();
		return restaurants;
	}

	@Override
	public Restaurant findById(Object id) {
		logger.debug("Executing " + this.getClass().getName() + ".findById(id)");
		Optional<Restaurant> restaurant = restaurantMongoDbRepo.findById((String)id);
		return restaurant.get();
	}

	@Override
	public Restaurant save(Restaurant restaurant) {
		logger.debug("Executing " + this.getClass().getName() + ".save(restaurant)");
		Restaurant newRestaurant = restaurantMongoDbRepo.save(restaurant);
		logger.debug("   id = " + newRestaurant.getId());
		logger.debug("   name = " + newRestaurant.getName());
		logger.debug("   address = " + newRestaurant.getAddress().getStreet() + ", "
				+ newRestaurant.getAddress().getZipCode() + " " + newRestaurant.getAddress().getCity());
		return newRestaurant;
	}
	
	@Override
	public void deleteById(Object id) {
		logger.debug("Executing " + this.getClass().getName() + ".deleteById(id)");
		restaurantMongoDbRepo.deleteById((String) id);
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
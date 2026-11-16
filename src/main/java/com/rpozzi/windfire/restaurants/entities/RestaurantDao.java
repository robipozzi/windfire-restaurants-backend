package com.rpozzi.windfire.restaurants.entities;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.rpozzi.windfire.common.dao.ADao;
import com.rpozzi.windfire.common.dao.ResourceNotFoundException;

@Component
public class RestaurantDao extends ADao<Restaurant> {
	private static final Logger logger = LoggerFactory.getLogger(RestaurantDao.class);
	@Autowired
	private IRestaurantRepository restaurantMongoDbRepo;

	@Override
	public Iterable<Restaurant> findAll() {
		logger.debug("Executing " + this.getClass().getName() + ".findAll()");
		Iterable<Restaurant> restaurants = restaurantMongoDbRepo.findAll();
		return restaurants;
	}

	@Override
	public Restaurant findById(Object id) {
		logger.debug("Executing " + this.getClass().getName() + ".findById(id) - id = " + id);
		Optional<Restaurant> restaurant = restaurantMongoDbRepo.findById((String) id);
		return restaurant.orElseThrow(() -> new ResourceNotFoundException(id));
	}

	@Override
	public Restaurant save(Restaurant restaurant) {
		logger.debug("Executing " + this.getClass().getName() + ".save(restaurant)");
		Restaurant newRestaurant = restaurantMongoDbRepo.save(restaurant);
		logger.debug("   id = " + newRestaurant.getId());
		logger.debug("   name = " + newRestaurant.getName());
		// Runs after the save: a failure here would answer 500 for a document that was stored
		Address address = newRestaurant.getAddress();
		if (address != null) {
			logger.debug("   address = " + address.getStreet() + ", " + address.getZipCode() + " " + address.getCity());
		}
		return newRestaurant;
	}

	@Override
	public void deleteById(Object id) {
		logger.debug("Executing " + this.getClass().getName() + ".deleteById(id) - id = " + id);
		// MongoRepository.deleteById is a no-op for unknown ids: check first so the caller gets a 404
		if (!restaurantMongoDbRepo.existsById((String) id)) {
			throw new ResourceNotFoundException(id);
		}
		restaurantMongoDbRepo.deleteById((String) id);
	}

}
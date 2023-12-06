package com.rpozzi.windfire.restaurants.service;

import org.springframework.stereotype.Component;
import com.rpozzi.windfire.common.dao.IDao;
import com.rpozzi.windfire.common.service.ABusinessService;
import com.rpozzi.windfire.restaurants.entities.Restaurant;

@Component
public class RestaurantService extends ABusinessService<Restaurant> {

	public RestaurantService(IDao<Restaurant> dao) {
		super(dao);
	}

	public Iterable<Restaurant> findAllRestaurants() {
		logger.debug("Executing " + this.getClass().getName() + ".findAllRestaurants()");
		Iterable<Restaurant> restaurants = dao.findAll();
		return restaurants;
	}

	public Restaurant findRestaurantById(String id) {
		logger.debug("Executing " + this.getClass().getName() + ".findRestaurantById(id)");
		Restaurant restaurant = dao.findById(id);
		return restaurant;
	}

	public Restaurant addRestaurant(Restaurant restaurant) {
		logger.debug("Executing " + this.getClass().getName() + ".addRestaurant(restaurant)");
		logger.info("Adding restaurant : " + restaurant.getName());
		Restaurant newRestaurant = dao.save(restaurant);
		logger.info("Restaurant added with id = " + newRestaurant.getId() + " !!!");
		return newRestaurant;
	}

	public void removeRestaurant(Restaurant restaurant) {
		logger.debug("Executing " + this.getClass().getName() + ".removeRestaurant()");
		dao.deleteById(restaurant.getId());
	}
}
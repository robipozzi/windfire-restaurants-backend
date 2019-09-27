package com.rp.windfire.restaurants.entities;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.rp.windfire.common.dao.ADao;

@Component
public class RestaurantDao extends ADao<Restaurant>{
	/*
	 * @Autowired private IRestaurantRepository restaurantDao;
	 */
	
	public Iterable<Restaurant> findAll() {
		logger.info("Executing RestaurantDao.findAll()");
		Iterable<Restaurant> restaurants = __findAll_fake();
		return restaurants;
	}
	
	public Restaurant save(Restaurant restaurant) {
		logger.info("Executing RestaurantDao.save(restaurant)");
		return null;
	}
	
	private Iterable<Restaurant> __findAll_fake() {
		logger.info("Executing RestaurantDao.__findAll_fake()");
		List<Restaurant> restaurants = new ArrayList<>();
		// Create Restaurant 1
		Restaurant restaurant1 = new Restaurant();
		restaurant1.setId(1);
		restaurant1.setName("Il Vecchio Tram");
		// Create Restaurant 2
		Restaurant restaurant2 = new Restaurant();
		restaurant2.setId(2);
		restaurant2.setName("Battipalo");
		// Create Restaurant List
		restaurants.add(restaurant1);
		restaurants.add(restaurant2);
		return restaurants;
	}

}
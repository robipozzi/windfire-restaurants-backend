package com.rpozzi.windfire.restaurants.entities;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import com.rpozzi.windfire.common.dao.ADao;

@Component
public class RestaurantDao extends ADao<Restaurant>{
	/*
	 * @Autowired private IRestaurantRepository restaurantDao;
	 */
	
	@Override
	public Iterable<Restaurant> findAll() {
		logger.info("Executing RestaurantDao.findAll()"); 
		Iterable<Restaurant> restaurants = __findAll_fake();
		return restaurants;
	}
	
	@Override
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
		restaurant1.setPhone("0321778914");
		restaurant1.setMobile("3489008762");
		restaurant1.setEmail("ilvecchiotram@gmail.com");
		restaurant1.setWebsite("www.ilvecchiotram.it");
		restaurant1.setCap("21312");
		restaurant1.setCity("Arona");
		restaurant1.setAddress("Piazza del Popolo 12");
		// Create Restaurant 2
		Restaurant restaurant2 = new Restaurant();
		restaurant2.setId(2);
		restaurant2.setName("Battipalo");
		restaurant2.setPhone("0321713829");
		restaurant2.setMobile("3457708391");
		restaurant2.setEmail("battipalo@gmail.com");
		restaurant2.setWebsite("www.battipalo.com");
		restaurant2.setCap("21429");
		restaurant2.setCity("Lesa");
		restaurant2.setAddress("Lungolago Marconi 27");
		// Create Restaurant List
		restaurants.add(restaurant1);
		restaurants.add(restaurant2);
		return restaurants;
	}

}
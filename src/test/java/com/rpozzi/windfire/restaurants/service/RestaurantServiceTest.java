package com.rpozzi.windfire.restaurants.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.rpozzi.windfire.common.dao.IDao;
import com.rpozzi.windfire.common.dao.ResourceNotFoundException;
import com.rpozzi.windfire.restaurants.entities.Restaurant;
import com.rpozzi.windfire.testsupport.Does;

@ExtendWith(MockitoExtension.class)
@DisplayName("Restaurants · Business service (mocked DAO)")
class RestaurantServiceTest {

	@Mock
	private IDao<Restaurant> dao;
	@InjectMocks
	private RestaurantService service;

	private static Restaurant restaurant(String id) {
		Restaurant restaurant = new Restaurant();
		restaurant.setId(id);
		restaurant.setName("Battipalo");
		return restaurant;
	}

	@Test
	@DisplayName("findAllRestaurants delegates to the DAO")
	@Does("dao.findAll() → [r1, r2]; service returns the same list")
	void findAll() {
		List<Restaurant> restaurants = List.of(restaurant("1"), restaurant("2"));
		when(dao.findAll()).thenReturn(restaurants);

		assertSame(restaurants, service.findAllRestaurants());
	}

	@Test
	@DisplayName("findRestaurantById delegates to the DAO")
	@Does("dao.findById(\"1\") → r; service returns r")
	void findById() {
		Restaurant restaurant = restaurant("1");
		when(dao.findById("1")).thenReturn(restaurant);

		assertSame(restaurant, service.findRestaurantById("1"));
	}

	@Test
	@DisplayName("findRestaurantById propagates not-found")
	@Does("dao.findById throws ResourceNotFoundException → the service lets it through for the 404 handler")
	void findByIdNotFound() {
		when(dao.findById("missing")).thenThrow(new ResourceNotFoundException("missing"));

		assertThrows(ResourceNotFoundException.class, () -> service.findRestaurantById("missing"));
	}

	@Test
	@DisplayName("addRestaurant returns the saved restaurant")
	@Does("dao.save(r) → stored copy with id; service returns the stored copy, not the input")
	void add() {
		Restaurant input = restaurant(null);
		Restaurant stored = restaurant("generated-id");
		when(dao.save(input)).thenReturn(stored);

		assertSame(stored, service.addRestaurant(input));
	}

	@Test
	@DisplayName("removeRestaurant deletes by id")
	@Does("service.removeRestaurant(r with id 42) → dao.deleteById(\"42\")")
	void remove() {
		service.removeRestaurant(restaurant("42"));

		verify(dao).deleteById("42");
	}

}

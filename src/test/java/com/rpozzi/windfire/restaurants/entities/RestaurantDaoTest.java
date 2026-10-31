package com.rpozzi.windfire.restaurants.entities;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.rpozzi.windfire.common.dao.ResourceNotFoundException;
import com.rpozzi.windfire.testsupport.Does;

@ExtendWith(MockitoExtension.class)
@DisplayName("Restaurants · DAO (mocked MongoDB repository)")
class RestaurantDaoTest {

	@Mock
	private IRestaurantRepository repository;
	@InjectMocks
	private RestaurantDao dao;

	private static Restaurant restaurant(String id) {
		Restaurant restaurant = new Restaurant();
		restaurant.setId(id);
		restaurant.setName("Battipalo");
		return restaurant;
	}

	@Test
	@DisplayName("findAll returns what the repository returns")
	@Does("repository.findAll() → [r1, r2]; dao.findAll() returns the same list")
	void findAll() {
		List<Restaurant> restaurants = List.of(restaurant("1"), restaurant("2"));
		when(repository.findAll()).thenReturn(restaurants);

		assertSame(restaurants, dao.findAll());
	}

	@Test
	@DisplayName("findById returns the stored restaurant")
	@Does("repository.findById(\"1\") → Optional.of(r); dao.findById(\"1\") returns r")
	void findByIdFound() {
		Restaurant restaurant = restaurant("1");
		when(repository.findById("1")).thenReturn(Optional.of(restaurant));

		assertSame(restaurant, dao.findById("1"));
	}

	@Test
	@DisplayName("findById of an unknown id throws ResourceNotFoundException")
	@Does("repository.findById(\"missing\") → Optional.empty(); dao throws ResourceNotFoundException (mapped to 404)")
	void findByIdMissing() {
		when(repository.findById("missing")).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> dao.findById("missing"));
	}

	@Test
	@DisplayName("save returns the stored copy")
	@Does("repository.save(r) → copy with generated id; dao.save(r) returns that copy")
	void save() {
		Restaurant input = restaurant(null);
		input.setAddress(new Address("28040", "Lesa", "Viale Vittorio Veneto, 2", "NO", "Piemonte", "Italia"));
		Restaurant stored = restaurant("generated-id");
		stored.setAddress(input.getAddress());
		when(repository.save(input)).thenReturn(stored);

		assertSame(stored, dao.save(input));
	}

	@Test
	@DisplayName("save tolerates a stored restaurant without address")
	@Does("repository.save returns a restaurant with null address → dao.save does not throw (the debug logging is null-safe)")
	void saveWithoutAddress() {
		Restaurant stored = restaurant("generated-id");
		when(repository.save(stored)).thenReturn(stored);

		assertDoesNotThrow(() -> dao.save(stored));
	}

	@Test
	@DisplayName("deleteById removes an existing restaurant")
	@Does("repository.existsById(\"1\") → true; repository.deleteById(\"1\") is called")
	void deleteExisting() {
		when(repository.existsById("1")).thenReturn(true);

		dao.deleteById("1");

		verify(repository).deleteById("1");
	}

	@Test
	@DisplayName("deleteById of an unknown id throws and deletes nothing")
	@Does("repository.existsById(\"missing\") → false; dao throws ResourceNotFoundException, repository.deleteById never called")
	void deleteMissing() {
		when(repository.existsById("missing")).thenReturn(false);

		assertThrows(ResourceNotFoundException.class, () -> dao.deleteById("missing"));
		verify(repository, never()).deleteById(anyString());
	}

}

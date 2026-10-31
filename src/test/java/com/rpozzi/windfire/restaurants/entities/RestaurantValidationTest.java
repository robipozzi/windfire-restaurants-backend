package com.rpozzi.windfire.restaurants.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.rpozzi.windfire.testsupport.Does;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Bean Validation constraints of Restaurant and Address, boundary by boundary, without Spring.
 */
@DisplayName("Restaurants · Field constraints")
class RestaurantValidationTest {

	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = FACTORY.getValidator();

	@AfterAll
	static void closeFactory() {
		FACTORY.close();
	}

	private static Restaurant validRestaurant() {
		return new Restaurant("Battipalo", new Address("28040", "Lesa", "Viale Vittorio Veneto, 2", "NO", "Piemonte", "Italia"),
				"Italian", "0321713829", "3457708391", "battipalo@example.com", "https://example.com");
	}

	private static Set<String> invalidFields(Consumer<Restaurant> change) {
		Restaurant restaurant = validRestaurant();
		change.accept(restaurant);
		return VALIDATOR.validate(restaurant).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toSet());
	}

	private static Arguments change(String description, Consumer<Restaurant> change) {
		return Arguments.of(description, change);
	}

	private static Arguments change(String description, Consumer<Restaurant> change, String field) {
		return Arguments.of(description, change, field);
	}

	static Stream<Arguments> acceptedValues() {
		return Stream.of(
				change("name of exactly 200 chars", r -> r.setName("n".repeat(200))),
				change("every address field at 200 chars", r -> r.setAddress(new Address("z".repeat(200), "c".repeat(200),
						"s".repeat(200), "p".repeat(200), "r".repeat(200), "c".repeat(200)))),
				change("empty address object", r -> r.setAddress(new Address())),
				change("cuisine, phone, mobile at 50 chars", r -> {
					r.setCuisine("c".repeat(50));
					r.setPhone("1".repeat(50));
					r.setMobile("3".repeat(50));
				}),
				change("optional fields null", r -> {
					r.setCuisine(null);
					r.setPhone(null);
					r.setMobile(null);
					r.setEmail(null);
					r.setWebsite(null);
				}),
				change("email empty (UI sends \"\")", r -> r.setEmail("")),
				change("website empty (UI sends \"\")", r -> r.setWebsite("")),
				change("website http://", r -> r.setWebsite("http://example.com/menu?day=1")),
				change("website upper-case scheme", r -> r.setWebsite("HTTPS://EXAMPLE.COM")),
				change("website of exactly 2048 chars", r -> r.setWebsite("https://" + "a".repeat(2040))));
	}

	static Stream<Arguments> rejectedValues() {
		return Stream.of(
				change("name null", r -> r.setName(null), "name"),
				change("name empty", r -> r.setName(""), "name"),
				change("name whitespace only", r -> r.setName("   "), "name"),
				change("name of 201 chars", r -> r.setName("n".repeat(201)), "name"),
				change("address null", r -> r.setAddress(null), "address"),
				change("address.zipCode of 201 chars", r -> r.getAddress().setZipCode("z".repeat(201)), "address.zipCode"),
				change("address.city of 201 chars", r -> r.getAddress().setCity("c".repeat(201)), "address.city"),
				change("address.street of 201 chars", r -> r.getAddress().setStreet("s".repeat(201)), "address.street"),
				change("address.province of 201 chars", r -> r.getAddress().setProvince("p".repeat(201)), "address.province"),
				change("address.region of 201 chars", r -> r.getAddress().setRegion("r".repeat(201)), "address.region"),
				change("address.country of 201 chars", r -> r.getAddress().setCountry("c".repeat(201)), "address.country"),
				change("cuisine of 51 chars", r -> r.setCuisine("c".repeat(51)), "cuisine"),
				change("phone of 51 chars", r -> r.setPhone("1".repeat(51)), "phone"),
				change("mobile of 51 chars", r -> r.setMobile("3".repeat(51)), "mobile"),
				change("email without @", r -> r.setEmail("battipalo.example.com"), "email"),
				change("email with two @", r -> r.setEmail("a@b@example.com"), "email"),
				change("website javascript:", r -> r.setWebsite("javascript:alert(1)"), "website"),
				change("website ftp://", r -> r.setWebsite("ftp://example.com"), "website"),
				change("website without scheme", r -> r.setWebsite("www.example.com"), "website"),
				change("website with a space", r -> r.setWebsite("https://exa mple.com"), "website"),
				change("website scheme only", r -> r.setWebsite("https://"), "website"),
				change("website of 2049 chars", r -> r.setWebsite("https://" + "a".repeat(2041)), "website"));
	}

	@Test
	@DisplayName("Reference restaurant is valid")
	@Does("Validates a fully populated restaurant → no constraint violations")
	void referenceRestaurantIsValid() {
		assertTrue(invalidFields(r -> { }).isEmpty());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("acceptedValues")
	@DisplayName("Accepted:")
	@Does("Changes one field of the reference restaurant to a boundary value → still no violations")
	void acceptedValue(String description, Consumer<Restaurant> change) {
		assertEquals(Set.of(), invalidFields(change));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rejectedValues")
	@DisplayName("Rejected:")
	@Does("Changes one field of the reference restaurant to an invalid value → exactly that field is reported")
	void rejectedValue(String description, Consumer<Restaurant> change, String field) {
		assertEquals(Set.of(field), invalidFields(change));
	}

}

package com.rpozzi.windfire.restaurants.entities;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IRestaurantRepository extends MongoRepository<Restaurant, String> {
	
}
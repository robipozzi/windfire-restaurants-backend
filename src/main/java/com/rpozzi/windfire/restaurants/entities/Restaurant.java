package com.rpozzi.windfire.restaurants.entities;

import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An entity to represent a restaurants.
 */
@Document("restaurants")
public class Restaurant {
	private String id;
	@NotBlank
	@Size(max = 200)
	private String name;
	@Valid
	private Address address;
	@Size(max = 50)
	private String cuisine;
	@Size(max = 50)
	private String phone;
	@Size(max = 50)
	private String mobile;
	@Email
	@Size(max = 254)
	private String email;
	@Size(max = 2048)
	@Pattern(regexp = "^(?i)https?://[^\\s]+$", message = "must be an http(s) URL")
	private String website;
	
	
	public Restaurant() {
		super();
	}
	
	public Restaurant(String name, Address address, String cuisine, String phone, String mobile, String email, String website) {
		super();
		this.name = name;
		this.address = address;
		this.cuisine = cuisine;
		this.phone = phone;
		this.mobile = mobile;
		this.email = email;
		this.website = website;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	public String getCuisine() {
		return cuisine;
	}

	public void setCuisine(String cuisine) {
		this.cuisine = cuisine;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getWebsite() {
		return website;
	}

	public void setWebsite(String website) {
		this.website = website;
	}
	
}
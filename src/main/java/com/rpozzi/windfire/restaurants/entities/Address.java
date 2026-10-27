package com.rpozzi.windfire.restaurants.entities;

import jakarta.validation.constraints.Size;

public class Address {
	@Size(max = 200)
	private String zipCode;
	@Size(max = 200)
	private String city;
	@Size(max = 200)
	private String street;
	@Size(max = 200)
	private String province;
	@Size(max = 200)
	private String region;
	@Size(max = 200)
	private String country;
	
	public Address() {
		super();
	}
	
	public Address(String zipCode, String city, String street, String province, String region, String country) {
		super();
		this.zipCode = zipCode;
		this.city = city;
		this.street = street;
		this.province = province;
		this.region = region;
		this.country = country;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getProvince() {
		return province;
	}

	public void setProvince(String province) {
		this.province = province;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}
}
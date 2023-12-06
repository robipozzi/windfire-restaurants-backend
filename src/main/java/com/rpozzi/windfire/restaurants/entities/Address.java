package com.rpozzi.windfire.restaurants.entities;

public class Address {
	private String zipCode;
	private String city;
	private String street;
	private String province;
	private String region;
	private String country;
	
	public Address() {
		super();
	}
	
	public Address(String zipCode, String city, String street) {
		super();
		this.zipCode = zipCode;
		this.city = city;
		this.street = street;
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
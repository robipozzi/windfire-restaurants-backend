package com.rpozzi.windfire.restaurants.entities;

//@Entity(name = "restaurants")
public class Restaurant {
	//@Id
	//@GeneratedValue(strategy = GenerationType.AUTO)
	private Integer id;
	//@Column(name = "NAME")
	private String name;
	//@Column(name = "PHONE")
	private String phone;
	//@Column(name = "MOBILE")
	private String mobile;
	//@Column(name = "EMAIL")
	private String email;
	//@Column(name = "WEBSITE")
	private String website;
	//@Column(name = "CAP")
	private String cap;
	//@Column(name = "CITY")
	private String city;
	//@Column(name = "ADDRESS")
	private String address;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public String getCap() {
		return cap;
	}

	public void setCap(String cap) {
		this.cap = cap;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

}
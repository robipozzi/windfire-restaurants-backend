package com.rpozzi.windfire.health.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import com.rpozzi.windfire.application.ApplicationProperties;

@RestController
public class Check {
	@Autowired
	Environment environment;

	@GetMapping("check")
	public @ResponseBody ResponseEntity<String> example() {
		List<String> list = new ArrayList<>();
		// return a simple list of strings
		list.add("Congratulations, your '" + environment.getProperty(ApplicationProperties.SPRING_APPLICATION_NAME)
				+ "' application is up and running");
		return new ResponseEntity<String>(list.toString(), HttpStatus.OK);
	}
}
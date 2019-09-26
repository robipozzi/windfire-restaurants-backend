package com.rp.windfire.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Example {
	//private IApplicationConfiguration bean;
	private static final Logger logger = LoggerFactory.getLogger(Example.class);

	/*
	 * public Example(IApplicationConfiguration bean) { super(); this.bean = bean; }
	 */

	@RequestMapping("v1")
    public @ResponseBody ResponseEntity<String> example() {
        List<String> list = new ArrayList<>();
        //return a simple list of strings
        list.add("Congratulations, your com.rp.windfire.application is up and running");
        //logger.info("###### appName = " + bean.getApplicationName());
        return new ResponseEntity<String>(list.toString(), HttpStatus.OK);
    }

}
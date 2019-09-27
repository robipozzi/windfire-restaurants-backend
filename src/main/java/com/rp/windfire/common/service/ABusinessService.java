package com.rp.windfire.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rp.windfire.common.dao.IDao;
import com.rp.windfire.config.IApplicationConfiguration;

public abstract class ABusinessService<T> implements IBusinessService<T> {
	protected static final Logger logger = LoggerFactory.getLogger(ABusinessService.class);
	protected IApplicationConfiguration config;
	protected IDao<T> dao;
	
	protected ABusinessService(IDao<T> dao, IApplicationConfiguration config) {
		super();
		this.config = config;
		this.dao = dao;
	}
	
}
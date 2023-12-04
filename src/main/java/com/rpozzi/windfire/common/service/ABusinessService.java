package com.rpozzi.windfire.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rpozzi.windfire.common.dao.IDao;

public abstract class ABusinessService<T> implements IBusinessService<T> {
	protected static final Logger logger = LoggerFactory.getLogger(ABusinessService.class);
	protected IDao<T> dao;

	protected ABusinessService(IDao<T> dao) {
		super();
		this.dao = dao;
	}
}
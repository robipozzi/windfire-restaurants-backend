package com.rp.windfire.common.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ADao<T> implements IDao<T> {
	protected Logger logger = LoggerFactory.getLogger(ADao.class);
}
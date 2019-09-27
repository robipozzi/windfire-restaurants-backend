package com.rp.windfire.common.dao;

public interface IDao<T> {
	public Iterable<T> findAll();
	public T save(T obj);
}
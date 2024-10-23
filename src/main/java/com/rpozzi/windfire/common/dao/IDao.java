package com.rpozzi.windfire.common.dao;

public interface IDao<T> {
	public Iterable<T> findAll();
	public T findById(Object id);
	public T save(T obj);
	public void deleteById(Object id);
}
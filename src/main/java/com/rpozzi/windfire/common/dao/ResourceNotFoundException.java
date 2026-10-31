package com.rpozzi.windfire.common.dao;

/**
 * Thrown when the requested entity does not exist; mapped to 404 by RestExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ResourceNotFoundException(Object id) {
		super("Resource not found: " + id);
	}
}

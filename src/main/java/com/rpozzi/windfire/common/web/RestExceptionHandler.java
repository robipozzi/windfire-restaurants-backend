package com.rpozzi.windfire.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.rpozzi.windfire.common.dao.ResourceNotFoundException;

@RestControllerAdvice
public class RestExceptionHandler {

	/**
	 * Returns the invalid fields and their constraint messages only: rejected values are never echoed back.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleValidationException(MethodArgumentNotValidException ex) {
		List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> Map.of(
						"field", fieldError.getField(),
						"message", String.valueOf(fieldError.getDefaultMessage())))
				.toList();

		Map<String, Object> errorResponse = new LinkedHashMap<>();
		errorResponse.put("error", "validation_failed");
		errorResponse.put("message", "Request body is invalid");
		errorResponse.put("fieldErrors", fieldErrors);
		return errorResponse;
	}

	/**
	 * The requested id is not echoed back, for the same reason as above.
	 */
	@ExceptionHandler(ResourceNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, Object> handleNotFoundException(ResourceNotFoundException ex) {
		Map<String, Object> errorResponse = new LinkedHashMap<>();
		errorResponse.put("error", "not_found");
		errorResponse.put("message", "Resource not found");
		return errorResponse;
	}

}

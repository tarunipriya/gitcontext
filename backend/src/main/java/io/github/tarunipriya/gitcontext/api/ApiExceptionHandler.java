package io.github.tarunipriya.gitcontext.api;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler translating application exceptions into RFC 7807 {@link ProblemDetail} responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/**
	 * Handles validation failures on request payloads.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		if (detail.isBlank()) {
			detail = "Validation failed";
		}
		log.warn("Validation failure: {}", detail);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Validation Failed");
		return problem;
	}

	/**
	 * Handles illegal arguments, such as malformed repository reference formats.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
		log.warn("Bad request argument: {}", ex.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Invalid Request");
		return problem;
	}

	/**
	 * Handles resource-not-found exceptions for missing jobs or repo summaries.
	 */
	@ExceptionHandler({JobNotFoundException.class, RepoNotFoundException.class})
	public ProblemDetail handleNotFoundException(RuntimeException ex) {
		log.warn("Resource not found: {}", ex.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Resource Not Found");
		return problem;
	}

	/**
	 * Fallback handler catching all unexpected internal server errors without leaking stack traces.
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleGenericException(Exception ex) {
		log.error("Unhandled internal server error: {}", ex.getMessage(), ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected internal server error occurred"
		);
		problem.setTitle("Internal Server Error");
		return problem;
	}
}

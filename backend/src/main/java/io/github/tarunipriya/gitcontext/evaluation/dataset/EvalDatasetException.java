package io.github.tarunipriya.gitcontext.evaluation.dataset;

/**
 * Unchecked exception thrown when an evaluation dataset file cannot be read, parsed, or validated.
 */
public class EvalDatasetException extends RuntimeException {

	/**
	 * Creates a new {@link EvalDatasetException} with the specified error message.
	 *
	 * @param message description of the validation or parsing failure
	 */
	public EvalDatasetException(String message) {
		super(message);
	}

	/**
	 * Creates a new {@link EvalDatasetException} with the specified error message and underlying cause.
	 *
	 * @param message description of the failure
	 * @param cause underlying cause
	 */
	public EvalDatasetException(String message, Throwable cause) {
		super(message, cause);
	}
}

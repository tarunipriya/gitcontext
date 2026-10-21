package io.github.tarunipriya.gitcontext.generation;

/**
 * Strategy interface for communicating with Large Language Models.
 */
public interface LlmClient {

	/**
	 * Generates completion for the given request.
	 *
	 * @param request prompting instructions and contents
	 * @return generated response and execution metadata
	 */
	LlmResponse generate(LlmRequest request);
}

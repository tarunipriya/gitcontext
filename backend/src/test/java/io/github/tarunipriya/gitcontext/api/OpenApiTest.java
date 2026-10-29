package io.github.tarunipriya.gitcontext.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("GET /v3/api-docs returns 200 and contains the ask endpoint path")
	void apiDocs_returns200AndContainsAskEndpoint() throws Exception {
		mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("GitContext API"))
				.andExpect(content().string(containsString("/api/repos/{owner}/{name}/ask")));
	}

	@Test
	@DisplayName("GET /swagger-ui.html returns redirect or success")
	void swaggerUi_isAvailable() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}
}

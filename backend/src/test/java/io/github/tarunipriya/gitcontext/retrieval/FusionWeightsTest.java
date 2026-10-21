package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FusionWeightsTest {

	@Test
	@DisplayName("FusionWeights defaults are 1.0 and 1.0")
	void defaults_returnOneAndOne() {
		FusionWeights weights = FusionWeights.defaults();
		assertThat(weights.vectorWeight()).isEqualTo(1.0);
		assertThat(weights.keywordWeight()).isEqualTo(1.0);

		FusionWeights nullPropWeights = FusionWeights.defaults(null);
		assertThat(nullPropWeights.vectorWeight()).isEqualTo(1.0);
		assertThat(nullPropWeights.keywordWeight()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("FusionWeights extracts configured weights from GitContextProperties")
	void defaults_fromProperties_extractsConfiguredWeights() {
		GitContextProperties.HybridProperties hybridProps = new GitContextProperties.HybridProperties(
				50, 60, SearchMode.HYBRID, 0.7, 0.3);
		GitContextProperties props = new GitContextProperties(
				null, null, "http://localhost:6333", null,
				null, null, null, null, null, null,
				hybridProps, null, null, null
		);

		FusionWeights weights = FusionWeights.defaults(props);
		assertThat(weights.vectorWeight()).isEqualTo(0.7);
		assertThat(weights.keywordWeight()).isEqualTo(0.3);
	}

	@Test
	@DisplayName("FusionWeights validates finite, >= 0, and not both 0")
	void validation_invalidWeights_throwsIllegalArgumentException() {
		assertThatThrownBy(() -> new FusionWeights(-0.1, 1.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vectorWeight must be finite and >= 0");

		assertThatThrownBy(() -> new FusionWeights(1.0, -0.5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("keywordWeight must be finite and >= 0");

		assertThatThrownBy(() -> new FusionWeights(Double.NaN, 1.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vectorWeight must be finite and >= 0");

		assertThatThrownBy(() -> new FusionWeights(1.0, Double.POSITIVE_INFINITY))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("keywordWeight must be finite and >= 0");

		assertThatThrownBy(() -> new FusionWeights(0.0, 0.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vectorWeight and keywordWeight cannot both be 0");
	}
}

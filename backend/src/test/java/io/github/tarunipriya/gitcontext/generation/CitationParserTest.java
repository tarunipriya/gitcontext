package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitationParserTest {

	private CitationParser parser;
	private List<Source> sources;

	@BeforeEach
	void setUp() {
		parser = new CitationParser();
		sources = List.of(
				new Source(1, createHit("c1", "File1.java", 10, 20, "fn1"), false),
				new Source(2, createHit("c2", "File2.java", 30, 40, "fn2"), false),
				new Source(3, createHit("c3", "File3.java", 50, 60, "fn3"), false)
		);
	}

	private SearchHit createHit(String chunkId, String path, int start, int end, String symbol) {
		return new SearchHit(
				"id-" + chunkId,
				0.8,
				"owner/repo",
				"run1",
				chunkId,
				"doc1",
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				path,
				"java",
				symbol,
				null,
				null,
				start,
				end,
				20,
				"https://github.com/owner/repo/blob/main/" + path,
				"sha1",
				"code",
				"code"
		);
	}

	@Test
	@DisplayName("parse extracts citations in order of first use and marks sources as cited")
	void parse_singleAndAdjacentMarkers_extractedInOrderOfFirstUse() {
		String answer = "First claim [2] and second claim [1][3]. Re-citation of [2] ignored.";

		CitationParser.CitationParseResult result = parser.parse(answer, sources);

		assertThat(result.citations()).hasSize(3);
		// Order of first use: 2, 1, 3
		assertThat(result.citations().get(0).number()).isEqualTo(2);
		assertThat(result.citations().get(0).path()).isEqualTo("File2.java");
		assertThat(result.citations().get(1).number()).isEqualTo(1);
		assertThat(result.citations().get(1).path()).isEqualTo("File1.java");
		assertThat(result.citations().get(2).number()).isEqualTo(3);
		assertThat(result.citations().get(2).path()).isEqualTo("File3.java");

		assertThat(result.invalidCitationsCount()).isEqualTo(0);
		assertThat(result.updatedSources()).allMatch(Source::cited);
	}

	@Test
	@DisplayName("parse handles comma-separated and dash-range citation formats")
	void parse_commaSeparatedAndDashRangeMarkers() {
		String answer = "Combined sources in [1, 3] and range [1-2].";

		CitationParser.CitationParseResult result = parser.parse(answer, sources);

		// Cited numbers: 1, 3 from [1, 3], then 2 from [1-2] (1 already present)
		assertThat(result.citations()).extracting(Citation::number)
				.containsExactly(1, 3, 2);
		assertThat(result.invalidCitationsCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("parse removes invalid citation numbers, cleans text, and reports count")
	void parse_invalidMarkersRemovedAndCounted() {
		String answer = "Valid claim [1]. Hallucinated claim [99].";

		CitationParser.CitationParseResult result = parser.parse(answer, sources);

		assertThat(result.invalidCitationsCount()).isEqualTo(1);
		assertThat(result.cleanedAnswer()).contains("Valid claim [1].");
		assertThat(result.cleanedAnswer()).doesNotContain("[99]");
		assertThat(result.citations()).hasSize(1);
		assertThat(result.citations().get(0).number()).isEqualTo(1);
	}

	@Test
	@DisplayName("parse removes invalid number inside mixed marker while preserving valid ones")
	void parse_subsetValidInMarker_keepsValidOnly() {
		String answer = "Mixed reference [1, 9] in text.";

		CitationParser.CitationParseResult result = parser.parse(answer, sources);

		assertThat(result.invalidCitationsCount()).isEqualTo(1);
		assertThat(result.cleanedAnswer()).contains("[1]");
		assertThat(result.cleanedAnswer()).doesNotContain("9");
		assertThat(result.citations()).hasSize(1);
	}

	@Test
	@DisplayName("parse ignores Markdown links and only matches bare citation brackets")
	void parse_markdownLinksNotConfusedWithCitations() {
		String answer = "Read [1](https://example.com) for external info, see [1] for code.";

		CitationParser.CitationParseResult result = parser.parse(answer, sources);

		assertThat(result.cleanedAnswer()).contains("[1](https://example.com)");
		assertThat(result.citations()).hasSize(1);
		assertThat(result.citations().get(0).number()).isEqualTo(1);
	}
}

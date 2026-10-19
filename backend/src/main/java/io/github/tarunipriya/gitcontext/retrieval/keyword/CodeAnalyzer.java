package io.github.tarunipriya.gitcontext.retrieval.keyword;

import java.io.IOException;
import java.io.Reader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.springframework.stereotype.Component;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.PRESERVE_ORIGINAL;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.SPLIT_ON_NUMERICS;

/**
 * Lucene {@link Analyzer} specialized for source code identifiers and natural language documentation.
 *
 * <p>Splits identifiers on camelCase, PascalCase, snake_case, kebab-case, dots, and alphanumeric boundaries
 * while preserving the composite identifier tokens. Normalises text to lower-case, removes standard English stop words,
 * applies light English minimal stemming (e.g. plural stripping so "limits" matches "limit"), and flattens
 * graph token streams for indexing and query parser compatibility.
 */
@Component
public class CodeAnalyzer extends Analyzer {

	private final CharArraySet stopWords;

	/**
	 * Creates a new {@link CodeAnalyzer} using Lucene's standard English stop words set.
	 */
	public CodeAnalyzer() {
		this(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
	}

	/**
	 * Creates a new {@link CodeAnalyzer} with a custom stop words set.
	 *
	 * @param stopWords set of stop words to filter out
	 */
	public CodeAnalyzer(CharArraySet stopWords) {
		this.stopWords = stopWords != null ? stopWords : EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer tokenizer = new StandardTokenizer();

		int flags = GENERATE_WORD_PARTS
				| GENERATE_NUMBER_PARTS
				| SPLIT_ON_CASE_CHANGE
				| SPLIT_ON_NUMERICS
				| PRESERVE_ORIGINAL;

		TokenStream stream = new WordDelimiterGraphFilter(tokenizer, flags, null);
		stream = new FlattenGraphFilter(stream);
		stream = new LowerCaseFilter(stream);
		stream = new StopFilter(stream, stopWords);
		stream = new EnglishMinimalStemFilter(stream);

		return new TokenStreamComponents(tokenizer, stream);
	}
}

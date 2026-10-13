package io.github.tarunipriya.gitcontext.chunking.treesitter;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.treesitter.TSNode;

/**
 * Encapsulates language-specific Tree-sitter grammar node-type rules and AST navigation.
 */
public interface LanguageSpec {

	/**
	 * Returns {@code true} if the given node represents a type/container declaration (class, interface, etc.).
	 */
	boolean isContainer(TSNode node);

	/**
	 * Returns {@code true} if the given node represents a callable (method, constructor, function, arrow function).
	 */
	boolean isCallable(TSNode node);

	/**
	 * Extracts the symbol name for a callable node.
	 */
	String extractCallableName(TSNode node, byte[] utf8Bytes);

	/**
	 * Extracts the name for a container node.
	 */
	String extractContainerName(TSNode node, byte[] utf8Bytes);

	/**
	 * Extracts the body node of a callable (e.g. block or statement_block) for skeleton replacement.
	 */
	TSNode getCallableBody(TSNode node);

	/**
	 * Extracts the body node of a container (e.g. class_body or block).
	 */
	TSNode getContainerBody(TSNode node);

	/**
	 * Unwraps decorated or export wrapper nodes (e.g. decorated_definition, export_statement)
	 * to return the inner core declaration node.
	 */
	TSNode unwrapNode(TSNode node);

	/**
	 * Returns the replacement string for a callable body when building a container skeleton.
	 * E.g. "{ ... }" for C-style languages, "..." for Python.
	 */
	String getBodySkeletonPlaceholder();

	/**
	 * Returns the {@link LanguageSpec} corresponding to the normalized language name.
	 */
	static LanguageSpec forLanguage(String language) {
		if (language == null || language.isBlank()) {
			return null;
		}
		String norm = language.trim().toLowerCase(Locale.ROOT);
		return switch (norm) {
			case "java" -> JavaLanguageSpec.INSTANCE;
			case "python", "py" -> PythonLanguageSpec.INSTANCE;
			case "javascript", "js" -> JavaScriptLanguageSpec.INSTANCE;
			case "typescript", "ts", "tsx" -> TypeScriptLanguageSpec.INSTANCE;
			default -> null;
		};
	}

	/**
	 * Helper extracting string content from a node using UTF-8 byte ranges.
	 */
	static String getNodeText(TSNode node, byte[] utf8Bytes) {
		if (node == null || node.isNull()) {
			return null;
		}
		int start = node.getStartByte();
		int end = node.getEndByte();
		if (start < 0 || end < start || end > utf8Bytes.length) {
			return null;
		}
		return new String(utf8Bytes, start, end - start, StandardCharsets.UTF_8).trim();
	}
}

/**
 * Java language AST node specification.
 */
final class JavaLanguageSpec implements LanguageSpec {
	static final JavaLanguageSpec INSTANCE = new JavaLanguageSpec();

	private static final Set<String> CONTAINERS = Set.of(
			"class_declaration",
			"interface_declaration",
			"enum_declaration",
			"record_declaration"
	);

	private static final Set<String> CALLABLES = Set.of(
			"method_declaration",
			"constructor_declaration",
			"compact_constructor_declaration"
	);

	private JavaLanguageSpec() {}

	@Override
	public boolean isContainer(TSNode node) {
		return node != null && !node.isNull() && CONTAINERS.contains(node.getType());
	}

	@Override
	public boolean isCallable(TSNode node) {
		return node != null && !node.isNull() && CALLABLES.contains(node.getType());
	}

	@Override
	public String extractContainerName(TSNode node, byte[] utf8Bytes) {
		if (node == null) return null;
		TSNode nameNode = node.getChildByFieldName("name");
		return LanguageSpec.getNodeText(nameNode, utf8Bytes);
	}

	@Override
	public String extractCallableName(TSNode node, byte[] utf8Bytes) {
		if (node == null) return null;
		TSNode nameNode = node.getChildByFieldName("name");
		return LanguageSpec.getNodeText(nameNode, utf8Bytes);
	}

	@Override
	public TSNode getCallableBody(TSNode node) {
		if (node == null) return null;
		return node.getChildByFieldName("body");
	}

	@Override
	public TSNode getContainerBody(TSNode node) {
		if (node == null) return null;
		return node.getChildByFieldName("body");
	}

	@Override
	public TSNode unwrapNode(TSNode node) {
		return node;
	}

	@Override
	public String getBodySkeletonPlaceholder() {
		return "{ ... }";
	}
}

/**
 * Python language AST node specification.
 */
final class PythonLanguageSpec implements LanguageSpec {
	static final PythonLanguageSpec INSTANCE = new PythonLanguageSpec();

	private PythonLanguageSpec() {}

	@Override
	public boolean isContainer(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		return unwrapped != null && !unwrapped.isNull() && "class_definition".equals(unwrapped.getType());
	}

	@Override
	public boolean isCallable(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		return unwrapped != null && !unwrapped.isNull() && "function_definition".equals(unwrapped.getType());
	}

	@Override
	public String extractContainerName(TSNode node, byte[] utf8Bytes) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		TSNode nameNode = unwrapped.getChildByFieldName("name");
		return LanguageSpec.getNodeText(nameNode, utf8Bytes);
	}

	@Override
	public String extractCallableName(TSNode node, byte[] utf8Bytes) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		TSNode nameNode = unwrapped.getChildByFieldName("name");
		return LanguageSpec.getNodeText(nameNode, utf8Bytes);
	}

	@Override
	public TSNode getCallableBody(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		return unwrapped.getChildByFieldName("body");
	}

	@Override
	public TSNode getContainerBody(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		return unwrapped.getChildByFieldName("body");
	}

	@Override
	public TSNode unwrapNode(TSNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if ("decorated_definition".equals(node.getType())) {
			TSNode definition = node.getChildByFieldName("definition");
			if (definition != null && !definition.isNull()) {
				return definition;
			}
			// Fallback: search named children for class_definition or function_definition
			for (int i = 0; i < node.getNamedChildCount(); i++) {
				TSNode child = node.getNamedChild(i);
				if ("class_definition".equals(child.getType()) || "function_definition".equals(child.getType())) {
					return child;
				}
			}
		}
		return node;
	}

	@Override
	public String getBodySkeletonPlaceholder() {
		return "...";
	}
}

/**
 * JavaScript language AST node specification.
 */
class JavaScriptLanguageSpec implements LanguageSpec {
	static final JavaScriptLanguageSpec INSTANCE = new JavaScriptLanguageSpec();

	protected static final Set<String> CONTAINERS = Set.of(
			"class_declaration",
			"abstract_class_declaration",
			"interface_declaration"
	);

	protected static final Set<String> CALLABLES = Set.of(
			"function_declaration",
			"method_definition",
			"generator_function_declaration"
	);

	protected JavaScriptLanguageSpec() {}

	@Override
	public boolean isContainer(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		return unwrapped != null && !unwrapped.isNull() && CONTAINERS.contains(unwrapped.getType());
	}

	@Override
	public boolean isCallable(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null || unwrapped.isNull()) {
			return false;
		}
		if (CALLABLES.contains(unwrapped.getType())) {
			return true;
		}
		// Check variable/lexical declaration: const x = () => {} or let f = function() {}
		if ("lexical_declaration".equals(unwrapped.getType()) || "variable_declaration".equals(unwrapped.getType())) {
			return getArrowOrFunctionFromVarDecl(unwrapped) != null;
		}
		return false;
	}

	@Override
	public String extractContainerName(TSNode node, byte[] utf8Bytes) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		TSNode nameNode = unwrapped.getChildByFieldName("name");
		return LanguageSpec.getNodeText(nameNode, utf8Bytes);
	}

	@Override
	public String extractCallableName(TSNode node, byte[] utf8Bytes) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		if ("lexical_declaration".equals(unwrapped.getType()) || "variable_declaration".equals(unwrapped.getType())) {
			TSNode varDeclarator = findVariableDeclarator(unwrapped);
			if (varDeclarator != null) {
				TSNode nameNode = varDeclarator.getChildByFieldName("name");
				return LanguageSpec.getNodeText(nameNode, utf8Bytes);
			}
		}
		TSNode nameNode = unwrapped.getChildByFieldName("name");
		return LanguageSpec.getNodeText(nameNode, utf8Bytes);
	}

	@Override
	public TSNode getCallableBody(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		if ("lexical_declaration".equals(unwrapped.getType()) || "variable_declaration".equals(unwrapped.getType())) {
			TSNode funcNode = getArrowOrFunctionFromVarDecl(unwrapped);
			if (funcNode != null) {
				return funcNode.getChildByFieldName("body");
			}
		}
		return unwrapped.getChildByFieldName("body");
	}

	@Override
	public TSNode getContainerBody(TSNode node) {
		TSNode unwrapped = unwrapNode(node);
		if (unwrapped == null) return null;
		return unwrapped.getChildByFieldName("body");
	}

	@Override
	public TSNode unwrapNode(TSNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if ("export_statement".equals(node.getType())) {
			TSNode decl = node.getChildByFieldName("declaration");
			if (decl != null && !decl.isNull()) {
				return decl;
			}
			for (int i = 0; i < node.getNamedChildCount(); i++) {
				TSNode child = node.getNamedChild(i);
				if (CONTAINERS.contains(child.getType()) || CALLABLES.contains(child.getType())
						|| "lexical_declaration".equals(child.getType()) || "variable_declaration".equals(child.getType())) {
					return child;
				}
			}
		}
		return node;
	}

	@Override
	public String getBodySkeletonPlaceholder() {
		return "{ ... }";
	}

	protected TSNode findVariableDeclarator(TSNode declNode) {
		for (int i = 0; i < declNode.getNamedChildCount(); i++) {
			TSNode child = declNode.getNamedChild(i);
			if ("variable_declarator".equals(child.getType())) {
				return child;
			}
		}
		return null;
	}

	protected TSNode getArrowOrFunctionFromVarDecl(TSNode declNode) {
		TSNode declarator = findVariableDeclarator(declNode);
		if (declarator == null) return null;
		TSNode value = declarator.getChildByFieldName("value");
		if (value != null && !value.isNull()) {
			String vType = value.getType();
			if ("arrow_function".equals(vType) || "function_expression".equals(vType) || "function".equals(vType)
					|| "generator_function".equals(vType)) {
				return value;
			}
		}
		for (int i = 0; i < declarator.getNamedChildCount(); i++) {
			TSNode child = declarator.getNamedChild(i);
			String cType = child.getType();
			if ("arrow_function".equals(cType) || "function_expression".equals(cType) || "function".equals(cType)
					|| "generator_function".equals(cType)) {
				return child;
			}
		}
		return null;
	}
}

/**
 * TypeScript language AST node specification.
 */
final class TypeScriptLanguageSpec extends JavaScriptLanguageSpec {
	static final TypeScriptLanguageSpec INSTANCE = new TypeScriptLanguageSpec();

	private TypeScriptLanguageSpec() {}
}

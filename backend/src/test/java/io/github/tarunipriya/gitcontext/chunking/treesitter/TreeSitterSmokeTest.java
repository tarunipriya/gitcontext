package io.github.tarunipriya.gitcontext.chunking.treesitter;

import org.junit.jupiter.api.Test;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterTypescript;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterSmokeTest {

	@Test
	void testJavaNodes() {
		try (TSParser parser = new TSParser()) {
			parser.setLanguage(new TreeSitterJava());
			String src = """
					package com.example;
					public class Foo {
						public Foo() {}
						public void bar() {}
					}
					record Person(String name) {
						public Person {}
					}
					enum Color { RED, GREEN }
					interface Greeter { void greet(); }
					""";
			TSTree tree = parser.parseString(null, src);
			TSNode root = tree.getRootNode();
			byte[] bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);

			// Foo class
			TSNode fooClass = root.getNamedChild(1);
			assertThat(JavaLanguageSpec.INSTANCE.isContainer(fooClass)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractContainerName(fooClass, bytes)).isEqualTo("Foo");

			TSNode classBody = JavaLanguageSpec.INSTANCE.getContainerBody(fooClass);
			assertThat(classBody).isNotNull();
			TSNode constructor = classBody.getNamedChild(0);
			assertThat(JavaLanguageSpec.INSTANCE.isCallable(constructor)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractCallableName(constructor, bytes)).isEqualTo("Foo");

			TSNode method = classBody.getNamedChild(1);
			assertThat(JavaLanguageSpec.INSTANCE.isCallable(method)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractCallableName(method, bytes)).isEqualTo("bar");

			// Record Person
			TSNode personRecord = root.getNamedChild(2);
			assertThat(JavaLanguageSpec.INSTANCE.isContainer(personRecord)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractContainerName(personRecord, bytes)).isEqualTo("Person");
			TSNode compactCtor = JavaLanguageSpec.INSTANCE.getContainerBody(personRecord).getNamedChild(0);
			assertThat(JavaLanguageSpec.INSTANCE.isCallable(compactCtor)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractCallableName(compactCtor, bytes)).isEqualTo("Person");

			// Enum Color
			TSNode colorEnum = root.getNamedChild(3);
			assertThat(JavaLanguageSpec.INSTANCE.isContainer(colorEnum)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractContainerName(colorEnum, bytes)).isEqualTo("Color");

			// Interface Greeter
			TSNode greeterInterface = root.getNamedChild(4);
			assertThat(JavaLanguageSpec.INSTANCE.isContainer(greeterInterface)).isTrue();
			assertThat(JavaLanguageSpec.INSTANCE.extractContainerName(greeterInterface, bytes)).isEqualTo("Greeter");
		}
	}

	@Test
	void testPythonNodes() {
		try (TSParser parser = new TSParser()) {
			parser.setLanguage(new TreeSitterPython());
			String src = """
					@decor
					class MyClass:
						@method_decor
						def my_method(self):
							pass

					@func_decor
					def my_func():
						pass
					""";
			TSTree tree = parser.parseString(null, src);
			TSNode root = tree.getRootNode();
			byte[] bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);

			// Decorated class
			TSNode classDef = root.getNamedChild(0);
			assertThat(PythonLanguageSpec.INSTANCE.isContainer(classDef)).isTrue();
			assertThat(PythonLanguageSpec.INSTANCE.extractContainerName(classDef, bytes)).isEqualTo("MyClass");

			TSNode classBody = PythonLanguageSpec.INSTANCE.getContainerBody(classDef);
			assertThat(classBody).isNotNull();
			TSNode methodDef = classBody.getNamedChild(0);
			assertThat(PythonLanguageSpec.INSTANCE.isCallable(methodDef)).isTrue();
			assertThat(PythonLanguageSpec.INSTANCE.extractCallableName(methodDef, bytes)).isEqualTo("my_method");

			// Decorated function
			TSNode funcDef = root.getNamedChild(1);
			assertThat(PythonLanguageSpec.INSTANCE.isCallable(funcDef)).isTrue();
			assertThat(PythonLanguageSpec.INSTANCE.extractCallableName(funcDef, bytes)).isEqualTo("my_func");
		}
	}

	@Test
	void testJsTsNodes() {
		try (TSParser parser = new TSParser()) {
			parser.setLanguage(new TreeSitterTypescript());
			String src = """
					export class TsClass {
						myMethod(): void {}
					}
					export const myArrow = () => { return 1; };
					const myExpr = function() {};
					function regularFunc() {}
					interface TsInterface { a: string; }
					abstract class AbstractClass {}
					""";
			TSTree tree = parser.parseString(null, src);
			TSNode root = tree.getRootNode();
			byte[] bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);

			TSNode tsClass = root.getNamedChild(0);
			assertThat(TypeScriptLanguageSpec.INSTANCE.isContainer(tsClass)).isTrue();
			assertThat(TypeScriptLanguageSpec.INSTANCE.extractContainerName(tsClass, bytes)).isEqualTo("TsClass");

			TSNode arrowDecl = root.getNamedChild(1);
			assertThat(TypeScriptLanguageSpec.INSTANCE.isCallable(arrowDecl)).isTrue();
			assertThat(TypeScriptLanguageSpec.INSTANCE.extractCallableName(arrowDecl, bytes)).isEqualTo("myArrow");

			TSNode exprDecl = root.getNamedChild(2);
			assertThat(TypeScriptLanguageSpec.INSTANCE.isCallable(exprDecl)).isTrue();
			assertThat(TypeScriptLanguageSpec.INSTANCE.extractCallableName(exprDecl, bytes)).isEqualTo("myExpr");

			TSNode funcDecl = root.getNamedChild(3);
			assertThat(TypeScriptLanguageSpec.INSTANCE.isCallable(funcDecl)).isTrue();
			assertThat(TypeScriptLanguageSpec.INSTANCE.extractCallableName(funcDecl, bytes)).isEqualTo("regularFunc");

			TSNode ifaceDecl = root.getNamedChild(4);
			assertThat(TypeScriptLanguageSpec.INSTANCE.isContainer(ifaceDecl)).isTrue();
			assertThat(TypeScriptLanguageSpec.INSTANCE.extractContainerName(ifaceDecl, bytes)).isEqualTo("TsInterface");

			TSNode absClass = root.getNamedChild(5);
			assertThat(TypeScriptLanguageSpec.INSTANCE.isContainer(absClass)).isTrue();
			assertThat(TypeScriptLanguageSpec.INSTANCE.extractContainerName(absClass, bytes)).isEqualTo("AbstractClass");
		}
	}
}

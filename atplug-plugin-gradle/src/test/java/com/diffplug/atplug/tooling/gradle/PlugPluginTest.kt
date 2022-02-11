package com.diffplug.atplug.tooling.gradle

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test

class PlugPluginTest : GradleIntegrationHarness() {
	@Test
	fun test() {
		val runtimeJar = File("../atplug-runtime/build/libs/atplug-runtime-0.1.0.jar").canonicalPath
		setFile("build.gradle")
				.toContent(
						"""
						plugins {
							id 'org.jetbrains.kotlin.jvm' version '1.6.10'
							id 'com.diffplug.atplug'
						}
						repositories {
							mavenCentral()
						}
						dependencies {
							implementation files("$runtimeJar")
							implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2"
						}
				""".trimIndent())

		val copy = { str: String ->
			val src = File("../atplug-runtime/src/test/java/com/diffplug/atplug/$str")
			setFile("src/main/java/com/diffplug/atplug/$str").toContent(Files.readString(src.toPath()))
		}
		copy("Fruit.kt")
		copy("Shape.java")

		gradleRunner().withArguments("jar").build()

		assertFile("src/main/resources/OSGI-INF/com.diffplug.atplug.Apple.xml")
				.hasContent(
						"{\n" +
								"    \"implementation\": \"com.diffplug.atplug.Apple\",\n" +
								"    \"provides\": \"com.diffplug.atplug.Fruit\",\n" +
								"    \"properties\": {\n" +
								"        \"id\": \"Apple\"\n" +
								"    }\n" +
								"}")
		assertFile("src/main/resources/OSGI-INF/com.diffplug.atplug.Orange.xml")
				.hasContent(
						"{\n" +
								"    \"implementation\": \"com.diffplug.atplug.Orange\",\n" +
								"    \"provides\": \"com.diffplug.atplug.Fruit\",\n" +
								"    \"properties\": {\n" +
								"        \"id\": \"Orange\"\n" +
								"    }\n" +
								"}")
		assertFile("src/main/resources/OSGI-INF/com.diffplug.atplug.Shape\$Circle.xml")
				.hasContent(
						"{\n" +
								"    \"implementation\": \"com.diffplug.atplug.Shape\$Circle\",\n" +
								"    \"provides\": \"com.diffplug.atplug.Shape\",\n" +
								"    \"properties\": {\n" +
								"        \"id\": \"Circle\"\n" +
								"    }\n" +
								"}")
		assertFile("src/main/resources/META-INF/MANIFEST.MF")
				.hasContent(
						"Manifest-Version: 1.0\n" +
								"Service-Component: OSGI-INF/com.diffplug.atplug.Apple.xml,OSGI-INF/com.d\n" +
								" iffplug.atplug.Orange.xml,OSGI-INF/com.diffplug.atplug.Shape\$Circle.xml\n" +
								" ,OSGI-INF/com.diffplug.atplug.Shape\$Square.xml")
	}
}

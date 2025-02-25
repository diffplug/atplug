package com.diffplug.atplug.tooling

import com.diffplug.atplug.tooling.gradle.ResourceHarness
import java.io.File
import java.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PlugGeneratorTest : ResourceHarness() {
	companion object {
		internal fun findRuntimeJar(): File {
			val folder = File("../atplug-runtime/build/libs")
			val files = folder.listFiles()
			Arrays.sort(files)
			return files.last { it.name.endsWith(".jar") && it.name.startsWith("atplug-runtime-") }
		}
	}

	fun deps(): Set<File> {
		val transitives =
				TestProvisioner.mavenCentral()
						.provisionWithTransitives(
								true,
								listOf(
										"org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1",
										"org.jetbrains.kotlin:kotlin-reflect:2.1.0"))
		val atplug_runtime = mutableSetOf(findRuntimeJar())
		atplug_runtime.addAll(transitives)
		return atplug_runtime
	}

	@Test
	fun generateMetadata() {
		val maps =
				PlugGenerator.generate(
						mapOf(
								"com.diffplug.atplug.Apple" to "com.diffplug.atplug.Fruit",
								"com.diffplug.atplug.Orange" to "com.diffplug.atplug.Fruit",
								"com.diffplug.atplug.Shape\$Circle" to "com.diffplug.atplug.Shape",
								"com.diffplug.atplug.Shape\$Square" to "com.diffplug.atplug.Shape"),
						listOf("java", "kotlin").map { File("../atplug-runtime/build/classes/$it/test") },
						deps())
		Assertions.assertEquals(
				"{com.diffplug.atplug.Apple={\n" +
						"    \"implementation\": \"com.diffplug.atplug.Apple\",\n" +
						"    \"provides\": \"com.diffplug.atplug.Fruit\",\n" +
						"    \"properties\": {\n" +
						"        \"id\": \"Apple\"\n" +
						"    }\n" +
						"}, com.diffplug.atplug.Orange={\n" +
						"    \"implementation\": \"com.diffplug.atplug.Orange\",\n" +
						"    \"provides\": \"com.diffplug.atplug.Fruit\",\n" +
						"    \"properties\": {\n" +
						"        \"id\": \"Orange\"\n" +
						"    }\n" +
						"}, com.diffplug.atplug.Shape\$Circle={\n" +
						"    \"implementation\": \"com.diffplug.atplug.Shape\$Circle\",\n" +
						"    \"provides\": \"com.diffplug.atplug.Shape\",\n" +
						"    \"properties\": {\n" +
						"        \"id\": \"Circle\"\n" +
						"    }\n" +
						"}, com.diffplug.atplug.Shape\$Square={\n" +
						"    \"implementation\": \"com.diffplug.atplug.Shape\$Square\",\n" +
						"    \"provides\": \"com.diffplug.atplug.Shape\",\n" +
						"    \"properties\": {\n" +
						"        \"id\": \"Square\"\n" +
						"    }\n" +
						"}}",
				maps.toString())
	}
}

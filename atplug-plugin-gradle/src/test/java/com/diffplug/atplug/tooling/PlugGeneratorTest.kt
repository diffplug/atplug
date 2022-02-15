package com.diffplug.atplug.tooling

import com.diffplug.atplug.tooling.gradle.ResourceHarness
import java.io.File
import java.util.*
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.testfixtures.ProjectBuilder
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
		val atplug_runtime = mutableSetOf(findRuntimeJar())
		val transitives =
				listOf(
						"org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2",
						"org.jetbrains.kotlin:kotlin-reflect:1.6.10")

		val userHome = File(System.getProperty("user.home"))
		val project =
				ProjectBuilder.builder()
						.withGradleUserHomeDir(File(userHome, ".gradle"))
						.withProjectDir(rootFolder())
						.build()

		val deps = transitives.map { project.dependencies.create(it) }.toTypedArray()

		project.repositories.mavenCentral()

		val config = project.configurations.detachedConfiguration(*deps)
		config.isTransitive = true
		config.description = "generation deps"
		config.attributes { attr: AttributeContainer ->
			attr.attribute(
					Bundling.BUNDLING_ATTRIBUTE,
					project.objects.named(Bundling::class.java, Bundling.EXTERNAL))
		}
		atplug_runtime.addAll(config.resolve())
		return atplug_runtime
	}

	@Test
	fun generateMetadata() {
		val maps =
				PlugGenerator.generate(
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

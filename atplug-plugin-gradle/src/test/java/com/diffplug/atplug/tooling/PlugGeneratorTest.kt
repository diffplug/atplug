package com.diffplug.atplug.tooling

import com.diffplug.atplug.tooling.gradle.ResourceHarness
import com.diffplug.common.base.StandardSystemProperty
import java.io.File
import org.assertj.core.api.Assertions
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class PlugGeneratorTest : ResourceHarness() {
  fun deps(): Set<File> {
    val atplug_runtime = mutableSetOf(File("../atplug-runtime/build/libs/atplug-runtime-0.1.0.jar"))
    val transitives = listOf("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    val userHome = File(StandardSystemProperty.USER_HOME.value())
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
    println((PlugGeneratorTest::class.java.classLoader).parent.parent)
    val maps =
        PlugGenerator.generate(
            listOf("java", "kotlin").map { File("../atplug-runtime/build/classes/$it/test") },
            deps())
    Assertions.assertThat(maps.toString())
        .isEqualTo(
            """
            {com.diffplug.atplug.Apple={
                "implementation": "com.diffplug.atplug.Apple",
                "provides": "com.diffplug.atplug.Fruit",
                "properties": {
                    "id": "Apple"
                }
            }, com.diffplug.atplug.Orange={
                "implementation": "com.diffplug.atplug.Orange",
                "provides": "com.diffplug.atplug.Fruit",
                "properties": {
                    "id": "Orange"
                }
            }, com.diffplug.atplug.Shape${"$"}Circle={
                "implementation": "com.diffplug.atplug.Shape${"$"}Circle",
                "provides": "com.diffplug.atplug.Shape",
                "properties": {
                    "id": "Circle"
                }
            }, com.diffplug.atplug.Shape${"$"}Square={
                "implementation": "com.diffplug.atplug.Shape${"$"}Square",
                "provides": "com.diffplug.atplug.Shape",
                "properties": {
                    "id": "Square"
                }
            }}
        """.trimIndent())
  }
}

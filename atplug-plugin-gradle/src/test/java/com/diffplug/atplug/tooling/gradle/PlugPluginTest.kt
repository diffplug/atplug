package com.diffplug.atplug.tooling.gradle

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test

class PlugPluginTest : GradleIntegrationHarness() {
    @Test
    fun test() {
        val runtimeJar = File("../atplug-runtime/build/libs/atplug-runtime-0.1.0.jar").canonicalPath
        val fruitExample = File("../atplug-runtime/src/test/java/com/diffplug/atplug/Fruit.kt")
        println(Files.readString(fruitExample.toPath()))
        setFile("build.gradle").toContent("""
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
        setFile("src/main/java/com/diffplug/atplug/Fruit.kt").toContent(Files.readString(fruitExample.toPath()))
        gradleRunner().withArguments("jar").build()

        assertFile("src/main/resources/OSGI-INF/com.diffplug.atplug.Apple.xml").hasContent("""
            {
                "implementation": "com.diffplug.atplug.Apple",
                "provides": "com.diffplug.atplug.Fruit",
                "properties": {
                    "id": "Apple"
                }
            }
        """.trimIndent())
        assertFile("src/main/resources/OSGI-INF/com.diffplug.atplug.Orange.xml").hasContent("""
            {
                "implementation": "com.diffplug.atplug.Orange",
                "provides": "com.diffplug.atplug.Fruit",
                "properties": {
                    "id": "Orange"
                }
            }
        """.trimIndent())
    }
}

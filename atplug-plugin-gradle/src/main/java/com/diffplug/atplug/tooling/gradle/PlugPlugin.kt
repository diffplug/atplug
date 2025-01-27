/*
 * Copyright (C) 2020-2023 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.atplug.tooling.gradle

import java.io.File
import java.io.Serializable
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar

/**
 * `plugGenerate` task uses `@Plug` to generate files in `src/main/resources/ATPLUG-INF` as a
 * dependency of `processResources`.
 */
class PlugPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		// get the classes we're compiling
		project.plugins.apply(JavaPlugin::class.java)
		val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
		val main = javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
		val plugin =
				project.plugins.findPlugin("org.jetbrains.kotlin.jvm")
						as org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
		val dep =
				project.dependencies.create("org.jetbrains.kotlin:kotlin-reflect:${plugin.pluginVersion}")
		val plugGenConfig =
				project.configurations.create("plugGenerate") { plugGen: Configuration ->
					plugGen.extendsFrom(
							project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))
					plugGen.dependencies.add(dep)
				}

		val findPlugsTask =
				project.tasks.register("findPlugs", FindPlugsTask::class.java) {
					it.classesFolders.setFrom(main.output.classesDirs)
					it.discoveredPlugsDir.set(project.layout.buildDirectory.dir("foundPlugs"))
					// dep on java
					for (taskName in mutableListOf("compileJava", "compileKotlin")) {
						try {
							it.dependsOn(project.tasks.named(taskName))
						} catch (e: UnknownTaskException) {
							// not a problem if we only have kotlin
						}
					}
				}

		val generatePlugsTask =
				project.tasks.register("generatePlugs", PlugGenerateTask::class.java) {
					it.discoveredPlugsDir.set(findPlugsTask.flatMap { it.discoveredPlugsDir })
					it.classesFolders.setFrom(main.output.classesDirs)
					it.jarsToLinkAgainst.setFrom(plugGenConfig)
					it.resourcesFolder = main.resources.sourceDirectories.singleFile
					it.dependsOn(findPlugsTask)
				}
		project.tasks.named(JavaPlugin.JAR_TASK_NAME).configure {
			val jarTask = it as Jar
			val metadataTask = generatePlugsTask.get()
			jarTask.inputs.dir(metadataTask.atplugInfFolder)
			jarTask.doFirst(
					"Set $SERVICE_COMPONENT header", SetServiceComponentHeader(metadataTask.atplugInfFolder))
		}
		project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).configure { t: Task ->
			t.dependsOn(generatePlugsTask)
		}
	}

	internal class SetServiceComponentHeader(private val atplugInfFolder: File) :
			Serializable, Action<Task> {
		override fun execute(task: Task) {
			val serviceComponents = PlugGenerateTask.atplugComponents(atplugInfFolder)
			val jarTask = task as Jar
			if (serviceComponents == null) {
				jarTask.manifest.attributes.remove(SERVICE_COMPONENT)
			} else {
				jarTask.manifest.attributes[SERVICE_COMPONENT] = serviceComponents
			}
		}
	}

	companion object {
		const val SERVICE_COMPONENT = "AtPlug-Component"
		const val DOT_JSON = ".json"
		const val ATPLUG_INF = "ATPLUG-INF/"
	}
}

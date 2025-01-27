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

import com.diffplug.atplug.tooling.PlugGeneratorJavaExecable
import com.diffplug.atplug.tooling.gradle.JavaExecable.Companion.exec
import com.diffplug.gradle.FileMisc
import com.diffplug.gradle.JRE
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.SortedMap
import java.util.jar.Attributes
import java.util.jar.Manifest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.JavaForkOptions
import org.gradle.work.Incremental
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkerExecutor

abstract class PlugGenerateTask : DefaultTask() {
	@get:Optional @get:Nested abstract val launcher: Property<JavaLauncher>

	@get:Inject abstract val workerExecutor: WorkerExecutor

	@get:InputDirectory abstract val discoveredPlugsDir: DirectoryProperty

	@get:InputFiles @get:Classpath abstract val jarsToLinkAgainst: ConfigurableFileCollection

	@get:Internal var resourcesFolder: File? = null

	@get:OutputDirectory
	val atplugInfFolder: File
		get() = File(resourcesFolder, PlugPlugin.ATPLUG_INF)

	@get:Incremental @get:InputFiles abstract val classesFolders: ConfigurableFileCollection

	init {
		this.outputs.upToDateWhen {
			val manifest = loadManifest()
			val componentsCmd = atplugComponents(atplugInfFolder)
			val componentsActual = manifest.mainAttributes.getValue(PlugPlugin.SERVICE_COMPONENT)
			componentsActual == componentsCmd
		}
		val spec = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
		val service = project.extensions.getByType(JavaToolchainService::class.java)
		launcher.set(service.launcherFor(spec))
	}

	@TaskAction
	fun build() {
		// 1) Collect the discovered classes
		val discoveredFiles =
				discoveredPlugsDir.get().asFile.listFiles().orEmpty().filter {
					it.isFile && it.name.endsWith(".txt")
				}

		// Turn them into a list of (plugClass, socketClass)
		val discoveredPlugs =
				discoveredFiles
						.map { file ->
							val line = file.readText(StandardCharsets.UTF_8).trim()
							val split = line.split("|")
							check(split.size == 2) { "Malformed discovered line in ${file.name}: '$line'" }
							val (plugClassName, socketClassName) = split
							plugClassName to socketClassName
						}
						.toMap()

		// 2) Use reflection logic, now that we have jarsToLinkAgainst, to produce final metadata
		//    This is where you'd adapt the old PlugGenerator invocation, but no scanning is needed
		if (discoveredPlugs.isEmpty()) {
			// no discovered plugs
			FileMisc.cleanDir(atplugInfFolder)
			return
		}

		// generate the metadata
		val result = generate(discoveredPlugs)

		// clean out the ATPLUG-INF folder, and put the map's content into the folder
		FileMisc.cleanDir(atplugInfFolder)
		for ((key, value) in result) {
			val serviceFile = File(atplugInfFolder, key + PlugPlugin.DOT_JSON)
			Files.write(serviceFile.toPath(), value.toByteArray(StandardCharsets.UTF_8))
		}

		// the resources directory *needs* the Service-Component entry of the manifest to exist in order
		// for tests to work
		// so we'll get a manifest (empty if necessary, but preferably we'll load what already exists)
		val manifest = loadManifest()
		val componentsCmd = atplugComponents(atplugInfFolder)
		val componentsActual = manifest.mainAttributes.getValue(PlugPlugin.SERVICE_COMPONENT)
		if (componentsActual == componentsCmd) {
			return
		}
		// make sure there is a MANIFEST_VERSION, because otherwise the manifest won't write *anything*
		if (manifest.mainAttributes.getValue(Attributes.Name.MANIFEST_VERSION) == null) {
			manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
		}
		// set the Service-Component entry
		if (componentsCmd == null) {
			manifest.mainAttributes.remove(Attributes.Name(PlugPlugin.SERVICE_COMPONENT))
		} else {
			manifest.mainAttributes.putValue(PlugPlugin.SERVICE_COMPONENT, componentsCmd)
		}
		// and write out the manifest
		saveManifest(manifest)
	}

	private fun manifestFile(): File = File(resourcesFolder, "META-INF/MANIFEST.MF")

	private fun loadManifest(): Manifest {
		val manifest = Manifest()
		if (manifestFile().isFile) {
			BufferedInputStream(Files.newInputStream(manifestFile().toPath())).use { input ->
				manifest.read(input)
			}
		}
		return manifest
	}

	private fun saveManifest(manifest: Manifest) {
		FileMisc.mkdirs(manifestFile().parentFile)
		BufferedOutputStream(Files.newOutputStream(manifestFile().toPath())).use { output ->
			manifest.write(output)
		}
	}

	private fun generate(discoveredPlugs: Map<String, String>): SortedMap<String, String> {
		val input =
				PlugGeneratorJavaExecable(
						discoveredPlugs, ArrayList(classesFolders.files), jarsToLinkAgainst.files)
		return if (launcher.isPresent) {
			val workQueue =
					workerExecutor.processIsolation { workerSpec: ProcessWorkerSpec ->
						workerSpec.classpath.from(fromLocalClassloader())
						workerSpec.forkOptions { options: JavaForkOptions ->
							options.setExecutable(launcher.get().executablePath)
						}
					}
			exec(workQueue, input).atplugInf!!
		} else {
			input.run()
			input.atplugInf!!
		}
	}

	companion object {
		fun atplugComponents(atplugInf: File): String? {
			return if (!atplugInf.isDirectory) null
			else {
				val serviceComponents: MutableList<String> = ArrayList()
				for (file in FileMisc.list(atplugInf)) {
					if (file.name.endsWith(PlugPlugin.DOT_JSON)) {
						serviceComponents.add(PlugPlugin.ATPLUG_INF + file.name)
					}
				}
				serviceComponents.sort()
				serviceComponents.joinToString(",")
			}
		}

		fun fromLocalClassloader(): Set<File> {
			val files = mutableSetOf<File>()
			val addPeerClasses = { clazz: Class<*> ->
				for (url in JRE.getClasspath(clazz.classLoader)) {
					val name = url.file
					if (name != null) {
						files.add(File(name))
					}
				}
			}
			// add the classes that we need
			addPeerClasses(PlugGeneratorJavaExecable::class.java)
			// add the gradle API
			addPeerClasses(JavaExec::class.java)
			// Needed because of Gradle API classloader hierarchy changes with 2c5adc8 in Gradle 6.7+
			addPeerClasses(FileCollection::class.java)
			return files
		}
	}
}

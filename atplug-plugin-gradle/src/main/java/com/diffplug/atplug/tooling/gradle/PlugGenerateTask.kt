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
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.function.Consumer
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.stream.Collectors
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.JavaForkOptions
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkerExecutor

abstract class PlugGenerateTask : DefaultTask() {
	@get:Optional @get:Nested abstract val launcher: Property<JavaLauncher>

	@get:Inject abstract val workerExecutor: WorkerExecutor

	@get:Inject abstract val fS: ObjectFactory?

	@get:InputFiles @get:Classpath abstract val jarsToLinkAgainst: ConfigurableFileCollection

	@get:Internal var resourcesFolder: File? = null

	@get:OutputDirectory
	val atplugInfFolder: File
		get() = File(resourcesFolder, PlugPlugin.ATPLUG_INF)

	@InputFiles var classesFolders: FileCollection? = null

	init {
		this.outputs.upToDateWhen { unused: Task? ->
			val manifest = loadManifest()
			val componentsCmd = atplugComponents()
			val componentsActual = manifest.mainAttributes.getValue(PlugPlugin.SERVICE_COMPONENT)
			componentsActual == componentsCmd
		}
		val spec = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
		val service = project.extensions.getByType(JavaToolchainService::class.java)
		launcher.set(service.launcherFor(spec))
	}

	fun setClassesFolders(files: Iterable<File>) {
		// if we don't copy, Gradle finds an implicit dependency which
		// forces us to depend on `classes` even though we don't
		val copy: MutableList<File> = ArrayList()
		for (file in files) {
			copy.add(file)
		}
		classesFolders = project.files(copy)
	}

	@TaskAction
	fun build() {
		// generate the metadata
		val result = generate()

		// clean out the ATPLUG-INF folder, and put the map's content into the folder
		FileMisc.cleanDir(atplugInfFolder)
		for ((key, value) in result!!) {
			val serviceFile = File(atplugInfFolder, key + PlugPlugin.DOT_JSON)
			Files.write(serviceFile.toPath(), value.toByteArray(StandardCharsets.UTF_8))
		}

		// the resources directory *needs* the Service-Component entry of the manifest to exist in order
		// for tests to work
		// so we'll get a manifest (empty if necessary, but preferably we'll load what already exists)
		val manifest = loadManifest()
		val componentsCmd = atplugComponents()
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
			try {
				BufferedInputStream(Files.newInputStream(manifestFile().toPath())).use { input ->
					manifest.read(input)
				}
			} catch (e: IOException) {
				throw RuntimeException(e)
			}
		}
		return manifest
	}

	private fun saveManifest(manifest: Manifest) {
		FileMisc.mkdirs(manifestFile().parentFile)
		try {
			BufferedOutputStream(Files.newOutputStream(manifestFile().toPath())).use { output ->
				manifest.write(output)
			}
		} catch (e: IOException) {
			throw RuntimeException(e)
		}
	}

	private fun generate(): SortedMap<String, String>? {
		val input =
				PlugGeneratorJavaExecable(ArrayList(classesFolders!!.files), jarsToLinkAgainst.files)
		return if (launcher.isPresent) {
			val workQueue =
					workerExecutor.processIsolation { workerSpec: ProcessWorkerSpec ->
						workerSpec.classpath.from(fromLocalClassloader())
						workerSpec.forkOptions { options: JavaForkOptions ->
							options.setExecutable(launcher.get().executablePath)
						}
					}
			exec(workQueue, input).atplugInf
		} else {
			input.run()
			input.atplugInf
		}
	}

	private fun atplugComponents(): String? {
		return atplugComponents(atplugInfFolder)
	}

	companion object {
		fun atplugComponents(atplugInf: File): String? {
			return if (!atplugInf.isDirectory) {
				null
			} else {
				val serviceComponents: MutableList<String> = ArrayList()
				for (file in FileMisc.list(atplugInf)) {
					if (file.name.endsWith(PlugPlugin.DOT_JSON)) {
						serviceComponents.add(PlugPlugin.ATPLUG_INF + file.name)
					}
				}
				Collections.sort(serviceComponents)
				serviceComponents.stream().collect(Collectors.joining(","))
			}
		}

		fun fromLocalClassloader(): Set<File> {
			val files: MutableSet<File> = LinkedHashSet()
			val addPeerClasses = Consumer { clazz: Class<*> ->
				try {
					for (url in JRE.getClasspath(clazz.classLoader)) {
						val name = url.file
						if (name != null) {
							files.add(File(name))
						}
					}
				} catch (e: Exception) {
					throw RuntimeException(e)
				}
			}
			// add the classes that we need
			addPeerClasses.accept(PlugGeneratorJavaExecable::class.java)
			// add the gradle API
			addPeerClasses.accept(JavaExec::class.java)
			// Needed because of Gradle API classloader hierarchy changes with 2c5adc8 in Gradle 6.7+
			addPeerClasses.accept(FileCollection::class.java)
			return files
		}
	}
}

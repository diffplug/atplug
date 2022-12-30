/*
 * Copyright (C) 2016-2022 DiffPlug
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
package com.diffplug.atplug.tooling

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.function.Consumer
import java.util.function.Supplier
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.testfixtures.ProjectBuilder

object TestProvisioner {
	fun gradleProject(dir: File): Project {
		val userHome = File(System.getProperty("user.home"))
		return ProjectBuilder.builder()
				.withGradleUserHomeDir(File(userHome, ".gradle"))
				.withProjectDir(dir)
				.build()
	}

	/**
	 * Creates a Provisioner for the given repositories.
	 *
	 * The first time a project is created, there are ~7 seconds of configuration which will go away
	 * for all subsequent runs.
	 *
	 * Every call to resolve will take about 1 second, even when all artifacts are resolved.
	 */
	private fun createWithRepositories(repoConfig: Consumer<RepositoryHandler>): Provisioner {
		// Running this takes ~3 seconds the first time it is called. Probably because of classloading.
		val tempDir = Files.createTempDirectory("temp").toFile()
		val project = gradleProject(tempDir)
		repoConfig.accept(project.repositories)
		return Provisioner { withTransitives: Boolean, mavenCoords: Collection<String> ->
			val deps = mavenCoords.map { project.dependencies.create(it) }.toTypedArray()
			val config = project.configurations.detachedConfiguration(*deps)
			config.isTransitive = withTransitives
			config.description = mavenCoords.toString()
			try {
				config.resolve()
			} catch (e: ResolveException) {
				/* Provide Maven coordinates in exception message instead of static string 'detachedConfiguration' */
				throw ResolveException(mavenCoords.toString(), e)
			} finally {
				// delete the temp dir
				Files.walk(tempDir.toPath())
						.sorted(Comparator.reverseOrder())
						.map { obj: Path -> obj.toFile() }
						.forEach { obj: File -> obj.delete() }
			}
		}
	}

	/** Creates a Provisioner which will cache the result of previous calls. */
	@Throws(FileNotFoundException::class)
	private fun caching(name: String, input: Supplier<Provisioner>): Provisioner {
		val spotlessDir = File(System.getProperty("user.dir")).parentFile
		val testlib = File(spotlessDir, "testlib")
		val cacheFile = File(testlib, "build/tmp/testprovisioner.$name.cache")
		var cached: MutableMap<Set<String>, Set<File>>
		if (cacheFile.exists()) {
			FileInputStream(cacheFile).use { file ->
				BufferedInputStream(file).use { buffer ->
					ObjectInputStream(buffer).use { inputStream ->
						cached = inputStream.readObject() as MutableMap<Set<String>, Set<File>>
					}
				}
			}
		} else {
			cached = HashMap()
			cacheFile.parentFile.mkdirs()
		}
		return Provisioner { withTransitives: Boolean, mavenCoordsRaw: Collection<String> ->
			val mavenCoords: Set<String> = LinkedHashSet(mavenCoordsRaw)
			synchronized(TestProvisioner::class.java) {
				var result = cached[mavenCoords]
				// double-check that depcache pruning hasn't removed them since our cache cached them
				val needsToBeSet =
						result == null ||
								!result.stream().allMatch { file: File ->
									file.exists() && file.isFile && file.length() > 0
								}
				if (needsToBeSet) {
					result = HashSet(input.get().provisionWithTransitives(withTransitives, mavenCoords))
					cached[mavenCoords] = result
					FileOutputStream(cacheFile).use { file ->
						BufferedOutputStream(file).use { buffered ->
							ObjectOutputStream(buffered).use { `object` -> `object`.writeObject(cached) }
						}
					}
				}
				result
			}
		}
	}

	/** Creates a Provisioner for the mavenCentral repo. */
	fun mavenCentral(): Provisioner {
		return createWithRepositories { repo: RepositoryHandler -> repo.mavenCentral() }
	}
}

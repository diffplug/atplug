package com.diffplug.atplug.tooling.gradle

import com.diffplug.atplug.tooling.PlugParser
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.*

/**
 * Incrementally scans compiled classes for @Plug usage and writes discovered plug classes into an
 * output directory.
 */
@CacheableTask
abstract class FindPlugsTask : DefaultTask() {
	@get:CompileClasspath
	@get:Incremental
	@get:InputFiles
	abstract val classesFolders: ConfigurableFileCollection

	/** Directory where we will store discovered plugs. */
	@get:OutputDirectory abstract val discoveredPlugsDir: DirectoryProperty

	@TaskAction
	fun findPlugs(inputChanges: InputChanges) {
		// If not incremental, clear everything and rescan
		if (!inputChanges.isIncremental) {
			discoveredPlugsDir.get().asFile.deleteRecursively()
		}

		// Make sure our output directory exists
		discoveredPlugsDir.get().asFile.mkdirs()

		// For each changed file in classesFolders, determine if it has @Plug
		val parser = PlugParser()
		for (change in inputChanges.getFileChanges(classesFolders)) {
			if (!change.file.name.endsWith(".class")) {
				continue
			}
			when (change.changeType) {
				ChangeType.REMOVED -> {
					// Remove old discovered data for this file
					removeOldMetadata(change)
				}
				ChangeType.ADDED,
				ChangeType.MODIFIED -> {
					parseAndWriteMetadata(parser, change, change.file)
				}
			}
		}
	}

	private fun parseAndWriteMetadata(parser: PlugParser, change: FileChange, classFile: File) {
		val plugToSocket = parser.parse(classFile)
		if (plugToSocket != null) {
			// For example: write a single line containing the discovered plug FQN
			val discoveredFile = discoveredPlugsDir.file(normalizePath(change)).get().asFile
			discoveredFile.writeText(plugToSocket.let { "${it.first}|${it.second}" })
		} else {
			// If previously discovered, remove it
			removeOldMetadata(change)
		}
	}

	private fun removeOldMetadata(change: FileChange) {
		// Remove any discovered file for the old .class
		val discoveredFile = discoveredPlugsDir.file(normalizePath(change)).get().asFile
		if (discoveredFile.exists()) {
			discoveredFile.delete()
		}
	}

	private fun normalizePath(change: FileChange) =
			change.normalizedPath.removeSuffix(".class").replace("/", "_")
}

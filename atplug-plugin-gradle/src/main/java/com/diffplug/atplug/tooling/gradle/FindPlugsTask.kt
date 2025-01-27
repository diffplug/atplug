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
					removeOldMetadata(change.file)
				}
				ChangeType.ADDED,
				ChangeType.MODIFIED -> {
					parseAndWriteMetadata(parser, change.file)
				}
			}
		}
	}

	private fun parseAndWriteMetadata(parser: PlugParser, classFile: File) {
		val plugToSocket = parser.parse(classFile)
		if (plugToSocket != null) {
			// For example: write a single line containing the discovered plug FQN
			val discoveredFile = discoveredPlugsDir.file(classFile.nameWithoutExtension).get().asFile
			if (discoveredFile.exists()) {
				val existing = discoveredFile.readText().split("|")
				check(existing[0] == plugToSocket.first) {
					"You need to rename one of these plugs because they have the same classfile name: ${existing[0]} and $plugToSocket"
				}
			} else {
				discoveredFile.parentFile.mkdirs()
			}
			discoveredFile.writeText(plugToSocket.let { "${it.first}|${it.second}" })
		} else {
			// If previously discovered, remove it
			removeOldMetadata(classFile)
		}
	}

	private fun removeOldMetadata(classFile: File) {
		// Remove any discovered file for the old .class
		val possibleName = classFile.nameWithoutExtension
		val discoveredFile = discoveredPlugsDir.file(possibleName).get().asFile
		if (discoveredFile.exists()) {
			discoveredFile.delete()
		}
	}
}

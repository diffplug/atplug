package com.diffplug.atplug.tooling.gradle

import com.diffplug.atplug.tooling.PlugParser
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.*
import org.gradle.work.*

/**
 * Incrementally scans compiled classes for @Plug usage and writes discovered plug classes into an
 * output directory.
 */
@CacheableTask
abstract class FindPlugsTask : DefaultTask() {
	@get:Classpath abstract val classesFolders: ConfigurableFileCollection

	/** Directory where we will store discovered plugs. */
	@get:OutputDirectory abstract val discoveredPlugsDir: DirectoryProperty

	@TaskAction
	fun findPlugs(inputChanges: InputChanges) {
		val parser = PlugParser()

		// If not incremental, clear everything and rescan
		if (inputChanges.isIncremental) {
			discoveredPlugsDir.get().asFile.mkdirs()
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
						parseAndWriteMetadata(parser, change)
					}
				}
			}
		} else {
			discoveredPlugsDir.get().asFile.deleteRecursively()
			discoveredPlugsDir.get().asFile.mkdirs()
			classesFolders.files.forEach { folder ->
				folder
						.walkTopDown()
						.filter { it.isFile && it.name.endsWith(".class") }
						.forEach { classFile ->
							val relativePath = classFile.toRelativeString(folder)
							parseAndWriteMetadata(
									parser,
									object : FileChange {
										override fun getFile(): File = classFile

										override fun getChangeType(): ChangeType = ChangeType.ADDED

										override fun getFileType(): FileType = FileType.FILE

										override fun getNormalizedPath(): String = relativePath
									})
						}
			}
		}
	}

	private fun parseAndWriteMetadata(parser: PlugParser, change: FileChange) {
		val plugToSocket = parser.parse(change.file)
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
			change.normalizedPath.removeSuffix(".class").replace("/", "_").replace("\\", "_")
}

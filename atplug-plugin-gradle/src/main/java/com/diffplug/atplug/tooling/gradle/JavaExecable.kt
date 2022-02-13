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
package com.diffplug.atplug.tooling.gradle

import com.diffplug.gradle.FileMisc
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.NotSerializableException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue

interface JavaExecable : Serializable, Runnable {
	interface PlugParameters : WorkParameters {
		val input: Property<JavaExecable>
		val outputFile: RegularFileProperty
	}

	abstract class PlugAction : WorkAction<PlugParameters> {
		override fun execute() {
			val gen = parameters.input.get()
			gen.run()
			write(parameters.outputFile.get().asFile, gen)
		}
	}

	/** Copies an exception hierarchy (class, message, and stacktrace). */
	class ThrowableCopy internal constructor(source: Throwable) :
			Throwable(source.javaClass.name + ": " + source.message) {
		init {
			stackTrace = source.stackTrace
			source.cause?.let { initCause(ThrowableCopy(it)) }
		}
	}

	companion object {
		@JvmStatic
		fun <T : JavaExecable> exec(queue: WorkQueue, input: T): T {
			val tempFile = File.createTempFile("JavaExecQueue", ".temp")
			queue.submit(PlugAction::class.java) { action: PlugParameters ->
				action.input.set(input)
				action.outputFile.set(tempFile)
			}
			queue.await()
			val result = read<T>(tempFile)
			FileMisc.forceDelete(tempFile)
			return result
		}

		@JvmStatic
		fun main(args: Array<String>) {
			val file = File(args[0])
			try {
				// read the target object from the file
				val javaExecOutside = read<JavaExecable>(file)
				// run the object's run method
				javaExecOutside.run()
				// save the object back to file
				write(file, javaExecOutside)
			} catch (t: Throwable) {
				// if it's an exception, write it out to file
				writeThrowable(file, t)
			}
		}

		/** Writes the given object to the given file. */
		fun <T : Serializable> write(file: File, obj: T) {
			ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use { output ->
				output.writeObject(obj)
			}
		}

		/** Reads an object from the given file. */
		fun <T : Serializable> read(file: File): T {
			ObjectInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
				return input.readObject() as T
			}
		}

		/** Writes an exception to file, even if that exception isn't serializable. */
		fun writeThrowable(file: File, obj: Throwable) {
			try {
				// write the exception as-is
				write(file, obj)
			} catch (e: NotSerializableException) {
				// if the exception is not serializable, then we'll
				// copy it in a way that is guaranteed to be serializable
				write(file, ThrowableCopy(obj))
			}
		}
	}
}

/*
 * Copyright (C) 2020-2022 DiffPlug
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

import java.io.File
import java.lang.ClassNotFoundException
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.lang.UnsatisfiedLinkError
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.Function

class PlugGenerator internal constructor(toSearches: List<File>, toLinkAgainst: Set<File>) {
	@JvmField val osgiInf: SortedMap<String, String> = TreeMap()

	/** A cache from a plugin interface to a function that converts a class into its metadata. */
	private val metadataCreatorCache = mutableMapOf<Class<*>, Function<Class<*>, String>>()

	private val classLoader: URLClassLoader
	private val socketClass: Class<*>

	init {
		// create a classloader which looks in toSearch first, then each of the jars in toLinkAgainst
		val urls = (toSearches + toLinkAgainst).map { it.toURI().toURL() }.toTypedArray()
		val parent: ClassLoader? =
				null // explicitly set parent to null so that the classloader is completely isolated
		classLoader = URLClassLoader(urls, parent)
		socketClass = classLoader.loadClass("com.diffplug.atplug.SocketOwner")
		try {
			val parser = PlugParser()
			// walk toSearch, passing each classfile to load()
			for (toSearch in toSearches) {
				if (toSearch.isDirectory) {
					Files.walkFileTree(
							toSearch.toPath(),
							object : SimpleFileVisitor<Path>() {
								override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
									if (file.toString().endsWith(EXT_CLASS)) {
										maybeGeneratePlugin(parser, file)
									}
									return FileVisitResult.CONTINUE
								}
							})
				}
			}
		} finally {
			classLoader.close()
		}
	}

	/**
	 * Loads a class by its FQN. If it's concrete and implements a plugin, then we'll call
	 * generatePlugin.
	 */
	private fun maybeGeneratePlugin(parser: PlugParser, path: Path) {
		parser.parse(path.toFile())
		if (!parser.hasPlug()) {
			return
		}
		val plugClass = classLoader.loadClass(parser.plugClassName)
		val socketClass = classLoader.loadClass(parser.socketClassName)
		require(!Modifier.isAbstract(plugClass.modifiers)) {
			"Class $plugClass has @Plug($socketClass) but it is abstract."
		}
		val osgiInfContent = generatePlugin<Any, Any>(plugClass, socketClass)
		osgiInf[plugClass.name] = osgiInfContent
	}

	private fun <SocketT, PlugT : SocketT?> generatePlugin(
			plugClass: Class<*>,
			socketClass: Class<*>
	): String {
		require(socketClass.isAssignableFrom(plugClass)) {
			"Socket $socketClass is not a supertype of @Plug $plugClass"
		}
		return generatePluginTyped(plugClass as Class<PlugT>, socketClass as Class<SocketT>)
	}

	/**
	 * @param plugClass The class for which we are generating plugin metadata.
	 * @param socketClass The interface which is the socket for the metadata.
	 * @return A string containing the content of OSGI-INF as appropriate for clazz.
	 */
	private fun <SocketT, PlugT : SocketT?> generatePluginTyped(
			plugClass: Class<PlugT>,
			socketClass: Class<SocketT>
	): String {
		val metadataCreator =
				metadataCreatorCache.computeIfAbsent(socketClass) { interfase: Class<*> ->
					var firstAttempt: Throwable? = null
					try {
						val socketOwnerClass = classLoader.loadClass(interfase.name + "\$Socket").kotlin
						val socket = socketOwnerClass.objectInstance!!
						return@computeIfAbsent generatorForSocket(socket)
					} catch (e: Throwable) {
						firstAttempt = e
					}
					try {
						val socketClazz = classLoader.loadClass(interfase.name)
						val socketField = socketClazz.getDeclaredField("socket")
						val socket = socketField[null]
						return@computeIfAbsent generatorForSocket(socket)
					} catch (secondAttempt: Throwable) {
						val e =
								IllegalArgumentException(
										"To create metadata for `$plugClass` we need either a field `socket` in `$interfase` or a kotlin `object Socket`.",
										firstAttempt)
						e.addSuppressed(secondAttempt)
						throw e
					}
				}
		return metadataCreator.apply(plugClass)
	}

	private fun generatorForSocket(socket: Any): Function<Class<*>, String> {
		val socketOwnerClazz: Class<*> = socket.javaClass
		val metadata = socketOwnerClazz.getMethod("asDescriptor", Any::class.java)
		metadata.isAccessible = true
		return Function { instanceClass: Class<*> ->
			try {
				metadata.invoke(socket, instantiate(instanceClass)) as String
			} catch (e: Exception) {
				if (rootCause(e) is ClassNotFoundException) {
					throw RuntimeException(
							"Unable to generate metadata for " +
									instanceClass +
									", missing transitive dependency " +
									rootCause(e).message,
							e)
				} else {
					throw RuntimeException(
							"Unable to generate metadata for " +
									instanceClass +
									", make sure that its metadata methods return simple constants: " +
									e.message,
							e)
				}
			}
		}
	}

	companion object {
		/**
		 * Returns a Map from a plugin's name to its OSGI-INF content.
		 *
		 * @param toSearch a directory containing class files where we will look for plugin
		 * implementations
		 * @param toLinkAgainst the classes that these plugins implementations need
		 * @return a map from component name to is OSGI-INF string content
		 */
		fun generate(toSearch: List<File?>?, toLinkAgainst: Set<File?>?): SortedMap<String, String> {
			return try {
				val ext = PlugGeneratorJavaExecable(toSearch, toLinkAgainst)
				val metadataGen = PlugGenerator(ext.toSearch, ext.toLinkAgainst)
				// save our results, with no reference to the guts of what happened inside PluginMetadataGen
				metadataGen.osgiInf
			} catch (e: Exception) {
				if (rootCause(e) is UnsatisfiedLinkError) {
					throw RuntimeException(
							"This is probably caused by a classpath sticking around from a previous invocation.  Run `gradlew --stop` and try again.",
							e)
				} else {
					throw e
				}
			}
		}

		const val EXT_CLASS = ".class"

		/** Calls the no-arg constructor of the given class, even if it is private. */
		fun <T> instantiate(clazz: Class<out T>): T {
			var constructor: Constructor<*>? = null
			for (candidate in clazz.declaredConstructors) {
				if (candidate.parameterCount == 0) {
					constructor = candidate
					break
				}
			}
			Objects.requireNonNull(
					constructor,
					"Class must have a no-arg constructor, but it didn't.  " +
							clazz +
							" " +
							Arrays.asList(*clazz.declaredConstructors))
			return constructor!!.newInstance() as T
		}

		private fun rootCause(e: Throwable): Throwable = e.cause?.let { rootCause(it) } ?: e
	}
}

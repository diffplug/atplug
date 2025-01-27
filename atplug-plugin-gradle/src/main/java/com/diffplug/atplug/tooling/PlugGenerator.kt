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
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.UnsatisfiedLinkError
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.*
import java.util.function.Function
import kotlin.reflect.KFunction
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.memberFunctions

class PlugGenerator
internal constructor(
		plugToSocket: Map<String, String>,
		toSearches: List<File>,
		toLinkAgainst: Set<File>
) {
	@JvmField val atplugInf: SortedMap<String, String> = TreeMap()

	/** A cache from a plugin interface to a function that converts a class into its metadata. */
	private val metadataCreatorCache = mutableMapOf<Class<*>, Function<Class<*>, String>>()
	private val classLoader: URLClassLoader
	private val socketOwnerCompanionObject: Any
	private val metadataGeneratorFor: KFunction<Function<Any, String>>

	init {
		// create a classloader which looks in toSearch first, then each of the jars in toLinkAgainst
		val urls = (toSearches + toLinkAgainst).map { it.toURI().toURL() }.toTypedArray()
		val parent: ClassLoader? =
				null // explicitly set parent to null so that the classloader is completely isolated
		classLoader = URLClassLoader(urls, parent)
		val socketOwner = classLoader.loadClass("com.diffplug.atplug.SocketOwner").kotlin
		socketOwnerCompanionObject = socketOwner.companionObjectInstance!!
		metadataGeneratorFor =
				socketOwnerCompanionObject::class.memberFunctions.find {
					it.name == "metadataGeneratorFor"
				}!!
						as KFunction<Function<Any, String>>
		try {
			plugToSocket.forEach { (plug, socket) -> atplugInf[plug] = generatePlugin(plug, socket) }
		} finally {
			classLoader.close()
			System.setProperty("atplug.generate", "")
		}
	}

	/**
	 * Loads a class by its FQN. If it's concrete and implements a plugin, then we'll call
	 * generatePlugin.
	 */
	private fun generatePlugin(plugClassName: String, socketClassName: String): String {
		val plugClass = classLoader.loadClass(plugClassName)
		val socketClass = classLoader.loadClass(socketClassName)
		require(!Modifier.isAbstract(plugClass.modifiers)) {
			"Class $plugClass has @Plug($socketClass) but it is abstract."
		}
		return generatePlugin<Any, Any>(plugClass, socketClass)
	}

	private fun <SocketT, PlugT : SocketT> generatePlugin(
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
	 * @return A string containing the content of ATPLUG-INF as appropriate for clazz.
	 */
	private fun <SocketT, PlugT : SocketT> generatePluginTyped(
			plugClass: Class<PlugT>,
			socketClass: Class<SocketT>
	): String {
		val metadataCreator =
				metadataCreatorCache.computeIfAbsent(socketClass) { interfase: Class<*> ->
					val generator = metadataGeneratorFor.call(socketOwnerCompanionObject, interfase)
					Function<Class<*>, String> { clazz -> generator.apply(instantiate(clazz)) }
				}
		return metadataCreator.apply(plugClass)
	}

	companion object {
		/**
		 * Returns a Map from a plugin's name to its ATPLUG-INF content.
		 *
		 * @param toSearch a directory containing class files where we will look for plugin
		 *   implementations
		 * @param toLinkAgainst the classes that these plugins implementations need
		 * @return a map from component name to is ATPLUG-INF string content
		 */
		fun generate(
				plugToSocket: Map<String, String>,
				toSearch: List<File>,
				toLinkAgainst: Set<File>
		): SortedMap<String, String> {
			return try {
				val ext = PlugGeneratorJavaExecable(plugToSocket, toSearch, toLinkAgainst)
				val metadataGen = PlugGenerator(plugToSocket, ext.toSearch, ext.toLinkAgainst)
				// save our results, with no reference to the guts of what happened inside PluginMetadataGen
				metadataGen.atplugInf
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
			requireNotNull(constructor) {
				"Class must have a no-arg constructor, but it didn't.  " +
						clazz +
						" " +
						listOf(*clazz.declaredConstructors)
			}
			return constructor.newInstance() as T
		}

		private fun rootCause(e: Throwable): Throwable = e.cause?.let { rootCause(it) } ?: e
	}
}

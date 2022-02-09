/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import java.lang.reflect.Constructor
import java.util.*
import java.util.function.Function

open class DeclarativeMetadataCreator<T>(
		/** The socket type. */
		val socket: Class<T>,
		/**
		 * Function which takes an instance of the socket type, and returns a map of its pruning
		 * properties.
		 */
		val metadataGenerator: Function<in T, Map<String, String>>
) {
	/** Returns the appropraite OSGI-INF for the given class. */
	fun configFor(clazz: Class<out T>): String {
		val instance = instantiate(clazz)
		val metadata = metadataGenerator.apply(instance)
		val component = PlugDescriptor(clazz.name, socket.name, metadata)
		return component.toJson()
	}

	companion object {
		@JvmStatic
		fun <T> instantiate(clazz: Class<out T>): T {
			var constructor: Constructor<*>? = null
			for (candidate in clazz.declaredConstructors) {
				if (candidate.parameterCount == 0) {
					constructor = candidate
					break
				}
			}
			assert(constructor != null) {
				"Class must have a no-arg constructor, but it didn't.  " +
						clazz +
						" " +
						Arrays.asList(*clazz.constructors)
			}
			return constructor!!.newInstance() as T
		}
	}
}

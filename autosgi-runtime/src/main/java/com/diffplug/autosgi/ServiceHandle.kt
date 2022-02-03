/*
 * Copyright (C) 2015-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi

import java.lang.AutoCloseable
import java.lang.IllegalStateException
import java.util.function.Supplier
import org.osgi.framework.ServiceReference

/** A handle to an open instance of an OSGi service. */
interface ServiceHandle<T> : AutoCloseable, Supplier<T> {
	/** Get rid of exceptions on AutoCloseable. */
	override fun close()
	class Imp<T>(val ref: ServiceReference<T>) : ServiceHandle<T> {
		var instance: T?

		override fun get() = instance ?: throw IllegalStateException("This handle has been closed.")

		override fun close() {
			instance = null
			ref.bundle.bundleContext.ungetService(ref)
		}

		init {
			instance = ref.bundle.bundleContext.getService(ref)
		}
	}

	companion object {
		/** Opens the given ServiceReference. */
		fun <T> open(ref: ServiceReference<T>): ServiceHandle<T> {
			return Imp(ref)
		}

		/** Wraps the given instance in the ServiceHandle API. */
		fun <T> mock(instance: T): ServiceHandle<T> {
			return object : ServiceHandle<T> {
				override fun get(): T {
					return instance
				}

				override fun close() {}
			}
		}
	}
}

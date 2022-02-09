/*
 * Copyright (C) 2017-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import java.lang.AutoCloseable
import java.lang.Exception
import kotlin.Throws
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** Creates a harness for local plugin setup. */
class PlugHarness : BeforeEachCallback, AfterEachCallback {
	var map = PlugInstanceMap()

	fun <T> add(clazz: Class<T>, instance: T): PlugHarness {
		map.put(clazz, instance)
		return this
	}

	fun start(): AutoCloseable {
		PlugRegistry.setHarness(map)
		return AutoCloseable { PlugRegistry.setHarness(null) }
	}

	private var openHarness: AutoCloseable? = null

	@Throws(Exception::class)
	override fun beforeEach(context: ExtensionContext) {
		assert(openHarness == null)
		openHarness = start()
	}

	@Throws(Exception::class)
	override fun afterEach(context: ExtensionContext) {
		openHarness!!.close()
		openHarness = null
	}

	companion object {
		@JvmStatic
		fun create(): PlugHarness {
			return PlugHarness()
		}
	}
}

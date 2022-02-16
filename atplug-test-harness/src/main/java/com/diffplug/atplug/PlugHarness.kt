/*
 * Copyright (C) 2017-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import java.lang.AutoCloseable

/** Creates a harness for local plugin setup. */
class PlugHarness {
	var map = PlugInstanceMap()

	fun <T> add(clazz: Class<T>, instance: T): PlugHarness {
		val descriptor = SocketOwner.metadataGeneratorFor(clazz).apply(instance)
		map.put(clazz, PlugDescriptor.fromJson(descriptor), instance)
		return this
	}

	fun start(): AutoCloseable {
		PlugRegistry.setHarness(map)
		return AutoCloseable { PlugRegistry.setHarness(null) }
	}

	fun junit5beforeAfterEach() = AtPlugJUnit5(this)
}

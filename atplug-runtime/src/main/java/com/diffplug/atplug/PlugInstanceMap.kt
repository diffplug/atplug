/*
 * Copyright (C) 2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

class PlugInstanceMap {
	internal val descriptorMap = mutableMapOf<String, MutableList<PlugDescriptor>>()
	internal val instanceMap = mutableMapOf<PlugDescriptor, Any>()

	fun putDescriptor(clazz: String, descriptor: PlugDescriptor) {
		val descriptors = descriptorMap.computeIfAbsent(clazz) { mutableListOf() }
		descriptors.add(descriptor)
	}

	fun <T> putInstance(clazz: Class<T>, descriptor: PlugDescriptor, instance: T) {
		putDescriptor(clazz.name, descriptor)
		instanceMap[descriptor] = instance!!
	}

	fun instanceFor(plugDescriptor: PlugDescriptor) = instanceMap[plugDescriptor]
}

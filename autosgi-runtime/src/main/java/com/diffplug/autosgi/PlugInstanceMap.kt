/*
 * Copyright (C) 2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi

class PlugInstanceMap {
	internal val descriptorMap = mutableMapOf<String, MutableList<PlugDescriptor>>()
	internal val instanceMap = mutableMapOf<PlugDescriptor, Any>()

	fun <T> put(clazz: Class<T>, instance: T) {
		val descriptors = descriptorMap.computeIfAbsent(clazz.name) { mutableListOf<PlugDescriptor>() }
		val creatorClazz = clazz.classLoader.loadClass(clazz.name + "\$MetadataCreator")
		val creator =
				DeclarativeMetadataCreator.instantiate(creatorClazz) as DeclarativeMetadataCreator<T>
		val map = creator.metadataGenerator.apply(instance)

		val instanceClassName = instance!!::class.java.name
		val descriptor = PlugDescriptor(instanceClassName, clazz.name, map)
		descriptors.add(descriptor)

		instanceMap.put(descriptor, instance)
	}

	fun put(clazz: String, descriptor: PlugDescriptor) {
		val descriptors = descriptorMap.computeIfAbsent(clazz) { mutableListOf<PlugDescriptor>() }
		descriptors.add(descriptor)
	}

	fun instanceFor(plugDescriptor: PlugDescriptor) = instanceMap[plugDescriptor]
}

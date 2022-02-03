/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi

import com.diffplug.autosgi.OsgiComponent.Companion.fromJson
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.function.Function
import java.util.jar.Manifest
import java.util.stream.Collectors
import java.util.stream.Stream
import org.osgi.framework.ServiceReference

class NonOsgiDS {
	private val byProvides = mutableMapOf<String, MutableList<OsgiComponent>>()
	private val instantiatedCache = mutableMapOf<Class<*>, List<*>>()

	init {
		val values = NonOsgiDS::class.java.classLoader.getResources(PATH_MANIFEST)
		while (values.hasMoreElements()) {
			val manifestUrl = values.nextElement()
			manifestUrl.openStream().use { stream ->
				// parse the manifest
				val manifest = Manifest(stream)
				val services = manifest.mainAttributes.getValue(DS_WITHIN_MANIFEST)
				if (services != null) {
					// it's got declarative services!
					for (service in
							services.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
						val servicePath = service.trim { it <= ' ' }
						if (!servicePath.isEmpty()) {
							val asString = manifestUrl.toExternalForm()
							if (asString.contains("org.eclipse.core.contenttype")) {
								// causes noisy errors
								continue
							}
							val component = parseComponent(asString, servicePath)
							val components = byProvides.computeIfAbsent(component.provides) { mutableListOf() }
							components.add(component)
						}
					}
				}
			}
		}
	}

	fun <T, D : ServiceDescriptor<T>?> getServices(
			clazz: Class<T>,
			constructor: Function<ServiceReference<T>, D>
	): Stream<D> {
		val list =
				instantiatedCache.computeIfAbsent(clazz) { cl: Class<*> ->
					val components = byProvides[clazz.name] ?: return@computeIfAbsent listOf<D>()
					components
							.stream()
							.flatMap { component: OsgiComponent ->
								// this is true iff its a valid service
								val implementation = Class.forName(component.implementation) as Class<out T>
								val value = DeclarativeMetadataCreator.instantiate(implementation)
								val reference = ServiceDescriptor.mockReference(value, component.properties)
								Stream.of(constructor.apply(reference))
							}
							.collect(Collectors.toList())
				} as
						List<D>
		return list.stream()
	}

	companion object {
		private const val PATH_MANIFEST = "META-INF/MANIFEST.MF"
		private const val DS_WITHIN_MANIFEST = "Service-Component"

		private fun parseComponent(manifestUrl: String, servicePath: String): OsgiComponent {
			val serviceUrl =
					URL(manifestUrl.substring(0, manifestUrl.length - PATH_MANIFEST.length) + servicePath)

			val out = ByteArrayOutputStream()
			serviceUrl.openStream().transferTo(out)
			val serviceFileContent = out.toString(StandardCharsets.UTF_8)
			return fromJson(serviceFileContent)
		}
	}
}

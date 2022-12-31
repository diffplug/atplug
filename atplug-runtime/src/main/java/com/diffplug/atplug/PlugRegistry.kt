/*
 * Copyright (C) 2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.lang.reflect.Constructor
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.jar.Manifest
import java.util.zip.ZipException

interface PlugRegistry {
	fun <T> registerSocket(socketClass: Class<T>, socketOwner: SocketOwner<T>)

	fun <T> instantiatePlug(socketClass: Class<T>, plugDescriptor: PlugDescriptor): T

	companion object {
		private val instance: Lazy<PlugRegistry> = lazy { Eager() }

		internal fun <T> registerSocket(socketClass: Class<T>, socketOwner: SocketOwner<T>) {
			instance.value.registerSocket(socketClass, socketOwner)
		}

		internal fun <T> instantiatePlug(socketClass: Class<T>, plugDescriptor: PlugDescriptor): T {
			return instance.value.instantiatePlug(socketClass, plugDescriptor)
		}

		private const val PATH_MANIFEST = "META-INF/MANIFEST.MF"
		private const val DS_WITHIN_MANIFEST = "AtPlug-Component"

		fun setHarness(data: PlugInstanceMap?) {
			val registry = instance.value
			if (registry is Eager) {
				registry.setHarness(data)
			} else {
				throw AssertionError("Registry must not be set, was $registry")
			}
		}
	}

	private class Eager : PlugRegistry {
		private val data = PlugInstanceMap()
		private val owners = mutableMapOf<String, SocketOwner<*>>()

		init {
			synchronized(this) {
				val values = Eager::class.java.classLoader.getResources(PATH_MANIFEST)
				while (values.hasMoreElements()) {
					val manifestUrl = values.nextElement()
					try {
						parseManifest(manifestUrl, true)
					} catch (e: EOFException) {
						// do the parsing again but this time disable caching
						// https://stackoverflow.com/questions/36517604/closing-a-jarurlconnection
						parseManifest(manifestUrl, false)
					} catch (e: ZipException) {
						// When a JVM loads a jar, it mmaps the jar. If that jar changes
						// (as it does when generating plugin metadata in a Gradle daemon)
						// then you sometimes get ZipException after the change.
						parseManifest(manifestUrl, false)
					}
				}
			}
		}

		private fun parseManifest(manifestUrl: URL, allowCaching: Boolean) {
			val connection = manifestUrl.openConnection()
			if (!allowCaching) {
				connection.useCaches = false
			}
			connection.getInputStream().use { stream ->
				// parse the manifest
				val manifest = Manifest(stream)
				val services = manifest.mainAttributes.getValue(DS_WITHIN_MANIFEST)
				if (services != null) {
					// it's got declarative services!
					for (service in
							services.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
						val servicePath = service.trim { it <= ' ' }
						if (servicePath.isNotEmpty()) {
							val asString = manifestUrl.toExternalForm()
							val component = parseComponent(asString, servicePath, allowCaching)
							synchronized(this) {
								data.putDescriptor(component.provides, component)
								owners[component.provides]?.doRegister(component)
							}
						}
					}
				}
			}
		}

		private fun parseComponent(
				manifestUrl: String,
				servicePath: String,
				allowCaching: Boolean
		): PlugDescriptor {
			val serviceUrl =
					URL(manifestUrl.substring(0, manifestUrl.length - PATH_MANIFEST.length) + servicePath)

			val connection = serviceUrl.openConnection()
			if (!allowCaching) {
				connection.useCaches = false
			}
			val out = ByteArrayOutputStream()
			connection.getInputStream().use { it.copyTo(out) }
			val serviceFileContent = String(out.toByteArray(), StandardCharsets.UTF_8)
			return PlugDescriptor.fromJson(serviceFileContent)
		}

		override fun <T> registerSocket(socketClass: Class<T>, socketOwner: SocketOwner<T>) {
			synchronized(this) {
				val prevOwner = owners.put(socketClass.name, socketOwner)
				assert(prevOwner == null) { "Multiple owners registered for $socketClass" }
				data.descriptorMap[socketClass.name]?.forEach(socketOwner::doRegister)
			}
		}

		override fun <T> instantiatePlug(socketClass: Class<T>, plugDescriptor: PlugDescriptor): T {
			val value =
					lastHarness?.instanceFor(plugDescriptor)
							?: instantiate(Class.forName(plugDescriptor.implementation))
			assert(socketClass.isInstance(value))
			return value as T
		}

		private fun <T> instantiate(clazz: Class<out T>): T {
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
						listOf(*clazz.constructors)
			}
			return constructor.newInstance() as T
		}

		private var lastHarness: PlugInstanceMap? = null

		fun setHarness(newHarness: PlugInstanceMap?) {
			val toRemove = lastHarness ?: data
			toRemove.descriptorMap.forEach { (clazz, plugDescriptors) ->
				owners[clazz]?.let { owner -> plugDescriptors.forEach(owner::doRemove) }
			}

			val toAdd = newHarness ?: data
			toAdd.descriptorMap.forEach { (clazz, plugDescriptors) ->
				owners[clazz]?.let { owner -> plugDescriptors.forEach(owner::doRegister) }
			}
			lastHarness = newHarness
		}
	}
}

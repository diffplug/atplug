/*
 * Copyright (C) 2015-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi

import com.diffplug.autosgi.DeclarativeMetadataCreator.Companion.instantiate
import com.diffplug.autosgi.ServiceHandle.Companion.mock
import com.diffplug.autosgi.ServiceHandle.Companion.open
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.ClassNotFoundException
import java.lang.StringBuilder
import java.lang.UnsupportedOperationException
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Stream
import kotlin.Throws
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.framework.Version

/** Describes an OSGi service. */
open class ServiceDescriptor<T>
/** Creates a ServiceDescriptor wrapped around the given reference. */
(val ref: ServiceReference<T>) {
	override fun equals(otherObj: Any?): Boolean {
		return if (otherObj is ServiceDescriptor<*>) {
			otherObj.ref == ref
		} else {
			false
		}
	}

	override fun hashCode(): Int {
		return ref.hashCode()
	}

	override fun toString(): String {
		val builder = StringBuilder()
		for (key in ref.propertyKeys) {
			builder.append(key)
			builder.append("=")
			builder.append(ref.getProperty(key))
			builder.append(" ")
		}
		return builder.toString()
	}

	protected fun getString(key: String?): String {
		return ref.getProperty(key) as String
	}

	/** Opens the service, and provides a handle for closing it when you're done. */
	fun open(): ServiceHandle<T> {
		return if (ref is MockReference<*>) {
			(ref as MockReference<T>).open()
		} else {
			open(ref)
		}
	}

	/**
	 * Opens the service, returns the service, then closes it. Should be used only for extremely
	 * light-weight services.
	 */
	fun unwrap(): T {
		return open().get()
	}

	/** Opens and closes the service safely. */
	fun openManaged(consumer: Consumer<T>) {
		open().use { handle -> consumer.accept(handle.get()) }
	}

	/** If this service descriptor is a mock, returns the mock value. */
	protected fun mockValue(): T? {
		return if (ref is MockReference<*>) {
			(ref as MockReference<T>).value
		} else {
			null
		}
	}

	private class MockReference<T>(val value: T, val props: Map<String, String>) :
			ServiceReference<T> {
		override fun equals(otherObj: Any?): Boolean {
			return if (otherObj is MockReference<*>) {
				val other = otherObj
				other.value == value && other.props == props
			} else {
				false
			}
		}

		override fun hashCode(): Int {
			return Objects.hash(value, props)
		}

		fun open(): ServiceHandle<T> {
			return mock(value)
		}

		override fun getProperty(key: String): Any {
			return props[key]!!
		}

		override fun getPropertyKeys(): Array<String> {
			return props.keys.toTypedArray()
		}

		override fun getBundle(): Bundle {
			// TODO: figure out .javaClass and then uncomment this
			//			val bundle: Bundle = FrameworkUtil.getBundle(value.javaClass)
			//			return bundle ?: MockBundle(value.javaClass)
			throw UnsupportedOperationException()
		}

		override fun getUsingBundles(): Array<Bundle> {
			throw UnsupportedOperationException()
		}

		override fun isAssignableTo(bundle: Bundle, className: String): Boolean {
			throw UnsupportedOperationException()
		}

		override fun compareTo(reference: Any): Int {
			throw UnsupportedOperationException()
		}

		override fun getProperties(): Dictionary<String, Any> {
			throw UnsupportedOperationException()
		}

		override fun <A> adapt(type: Class<A>): A {
			throw UnsupportedOperationException()
		}
	}

	private class MockBundle internal constructor(private val anchorClass: Class<*>) : Bundle {
		override fun getResource(name: String): URL {
			return anchorClass.getResource("/$name")
		}

		override fun compareTo(o: Bundle): Int {
			throw UnsupportedOperationException()
		}

		override fun getState(): Int {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun start(options: Int) {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun start() {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun stop(options: Int) {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun stop() {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun update(input: InputStream) {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun update() {
			throw UnsupportedOperationException()
		}

		@Throws(BundleException::class)
		override fun uninstall() {
			throw UnsupportedOperationException()
		}

		override fun getHeaders(): Dictionary<String, String> {
			throw UnsupportedOperationException()
		}

		override fun getBundleId(): Long {
			throw UnsupportedOperationException()
		}

		override fun getLocation(): String {
			throw UnsupportedOperationException()
		}

		override fun getRegisteredServices(): Array<ServiceReference<*>> {
			throw UnsupportedOperationException()
		}

		override fun getServicesInUse(): Array<ServiceReference<*>> {
			throw UnsupportedOperationException()
		}

		override fun hasPermission(permission: Any): Boolean {
			throw UnsupportedOperationException()
		}

		override fun getHeaders(locale: String): Dictionary<String, String> {
			throw UnsupportedOperationException()
		}

		override fun getSymbolicName(): String {
			throw UnsupportedOperationException()
		}

		@Throws(ClassNotFoundException::class)
		override fun loadClass(name: String): Class<*> {
			throw UnsupportedOperationException()
		}

		@Throws(IOException::class)
		override fun getResources(name: String): Enumeration<URL> {
			throw UnsupportedOperationException()
		}

		override fun getEntryPaths(path: String): Enumeration<String> {
			throw UnsupportedOperationException()
		}

		override fun getEntry(path: String): URL {
			throw UnsupportedOperationException()
		}

		override fun getLastModified(): Long {
			throw UnsupportedOperationException()
		}

		override fun findEntries(
				path: String,
				filePattern: String,
				recurse: Boolean
		): Enumeration<URL> {
			throw UnsupportedOperationException()
		}

		override fun getBundleContext(): BundleContext {
			throw UnsupportedOperationException()
		}

		override fun getSignerCertificates(
				signersType: Int
		): Map<X509Certificate, List<X509Certificate>> {
			throw UnsupportedOperationException()
		}

		override fun getVersion(): Version {
			throw UnsupportedOperationException()
		}

		override fun <A> adapt(type: Class<A>): A {
			throw UnsupportedOperationException()
		}

		override fun getDataFile(filename: String): File {
			throw UnsupportedOperationException()
		}
	}

	companion object {
		/** Test harness set by `PlugHarness`. */
		@JvmField var harness: ServiceDescriptorHarness? = null
		var nonOsgiDS: NonOsgiDS? = null

		/** Returns all ServiceDescriptors which are appropriate for the given class. */
		@JvmStatic
		protected fun <T, D : ServiceDescriptor<T>> getServices(
				clazz: Class<T>,
				constructor: Function<ServiceReference<T>, D>
		): Stream<D> {
			if (harness != null) {
				return harness!!.getServices(clazz, constructor)
			}
			synchronized(ServiceDescriptor::class.java) {
				if (nonOsgiDS != null) {
					return nonOsgiDS!!.getServices(clazz, constructor)
				}
			}
			val bundle = FrameworkUtil.getBundle(clazz)
			if (bundle != null) {
				val context = bundle.bundleContext
				if (context != null) {
					val serviceReferences = context.getServiceReferences(clazz, null)
					return serviceReferences.stream().map(constructor)
				}
			}
			synchronized(ServiceDescriptor::class.java) {
				// if we got to here, it seems we're not in an OSGi environment, switching to non-OSGi
				if (nonOsgiDS == null) {
					nonOsgiDS = NonOsgiDS()
				}
				return nonOsgiDS!!.getServices(clazz, constructor)
			}
		}
		/////////////
		// MOCKING //
		/////////////
		/** Creates a ServiceReference around the given value, with the given properties. */
		@JvmStatic
		fun <Socket, T : Socket> mockReference(
				value: T,
				socket: Class<Socket>
		): ServiceReference<Socket> {
			val creator =
					instantiate(socket.classLoader.loadClass(socket.name + "\$MetadataCreator")) as
							DeclarativeMetadataCreator<T>
			val map = creator.metadataGenerator.apply(value)
			return mockReference<Socket>(value, map)
		}

		@JvmStatic
		fun <T> mockReference(value: T, properties: Map<String, String>): ServiceReference<T> {
			return MockReference(value, properties)
		}
	}
}

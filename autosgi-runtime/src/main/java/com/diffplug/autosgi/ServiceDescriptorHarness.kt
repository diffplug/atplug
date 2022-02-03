/*
 * Copyright (C) 2021-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi

import java.util.function.Function
import java.util.stream.Stream
import org.osgi.framework.ServiceReference

interface ServiceDescriptorHarness {
	fun <T, D : ServiceDescriptor<T>> getServices(
			socket: Class<T>,
			constructor: Function<ServiceReference<T>, D>
	): Stream<D>
}

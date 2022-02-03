/*
 * Copyright (C) 2017-2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.osgi.framework.ServiceReference;

import com.diffplug.common.base.Preconditions;
import com.diffplug.common.collect.LinkedHashMultimap;
import com.diffplug.common.collect.Multimap;

/** Creates a harness for local plugin setup. */
public class PlugHarness implements BeforeEachCallback, AfterEachCallback {
	public static PlugHarness create() {
		return new PlugHarness();
	}

	Multimap<Class<?>, Object> map = LinkedHashMultimap.create();
	private final ServiceDescriptorHarness impl = new ServiceDescriptorHarness() {
		@Override
		public <T, D extends ServiceDescriptor<T>> Stream<D> getServices(Class<T> socket, Function<ServiceReference<T>, D> constructor) {
			return map.get(socket).stream().map(raw -> {
				@SuppressWarnings("unchecked")
				T obj = (T) raw;
				ServiceReference<T> desc = ServiceDescriptor.mockReference(obj, socket);
				return constructor.apply(desc);
			});
		}
	};

	public <T> PlugHarness add(Class<T> clazz, T instance) {
		map.put(clazz, instance);
		return this;
	}

	public AutoCloseable start() {
		ServiceDescriptor.harness = impl;
		return new AutoCloseable() {
			@Override
			public void close() {
				ServiceDescriptor.harness = null;
			}
		};
	}

	private AutoCloseable openHarness;

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		Preconditions.checkArgument(openHarness == null);
		openHarness = start();
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		openHarness.close();
		openHarness = null;
	}
}

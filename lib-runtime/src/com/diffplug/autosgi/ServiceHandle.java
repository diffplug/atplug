/*
 * Copyright 2018 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.autosgi;

import java.util.function.Supplier;

import org.osgi.framework.ServiceReference;

/** A handle to an open instance of an OSGi service. */
public interface ServiceHandle<T> extends AutoCloseable, Supplier<T> {
	/** Get rid of exceptions on AutoCloseable. */
	@Override
	void close();

	/** Opens the given ServiceReference. */
	static <T> ServiceHandle<T> open(ServiceReference<T> ref) {
		return new Imp<T>(ref);
	}

	static class Imp<T> implements ServiceHandle<T> {
		final ServiceReference<T> ref;
		T instance;

		public Imp(ServiceReference<T> ref) {
			this.ref = ref;
			instance = ref.getBundle().getBundleContext().getService(ref);
		}

		@Override
		public T get() {
			if (instance == null) {
				throw new IllegalStateException("This handle has been closed.");
			} else {
				return instance;
			}
		}

		@Override
		public void close() {
			instance = null;
			ref.getBundle().getBundleContext().ungetService(ref);
		}
	}

	/** Wraps the given instance in the ServiceHandle API. */
	static <T> ServiceHandle<T> mock(T instance) {
		return new ServiceHandle<T>() {
			@Override
			public T get() {
				return instance;
			}

			@Override
			public void close() {}
		};
	}
}

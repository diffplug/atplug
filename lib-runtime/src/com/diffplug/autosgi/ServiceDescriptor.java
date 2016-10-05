/*
 * Copyright 2016 DiffPlug
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Widget;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import com.diffplug.closet.util.DpLogger;
import com.diffplug.closet.util.DpLogger.Severity;
import com.diffplug.closet.util.Globals;
import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Preconditions;

/** Describes an OSGi service. */
public class ServiceDescriptor<T> {
	final ServiceReference<T> ref;

	/** Creates a ServiceDescriptor wrapped around the given reference. */
	public ServiceDescriptor(ServiceReference<T> ref) {
		this.ref = ref;
	}

	protected String getString(String key) {
		return (String) ref.getProperty(key);
	}

	protected URL getResource(String key) {
		String resource = getString(key);
		Objects.requireNonNull(resource);
		return ref.getBundle().getResource(resource);
	}

	/** Opens the service, and provides a handle for closing it when you're done. */
	public ServiceHandle<T> open() {
		if (ref instanceof MockReference) {
			return ((MockReference<T>) ref).open();
		} else {
			return ServiceHandle.open(ref);
		}
	}

	/** Opens the service, and automatically disposes when the widget is disposed. */
	public T openManagedWith(Widget widget) {
		Preconditions.checkArgument(!widget.isDisposed());
		ServiceHandle<T> handle = open();
		widget.addListener(SWT.Dispose, e -> {
			handle.close();
		});
		return handle.get();
	}

	/** Opens and closes the service safely. */
	public void openManaged(Consumer<T> consumer) {
		try (ServiceHandle<T> handle = open()) {
			consumer.accept(handle.get());
		}
	}

	/** Returns all ServiceDescriptors which are appropriate for the given class. */
	protected static <T, D extends ServiceDescriptor<T>> Stream<D> getServices(Class<T> clazz, Function<ServiceReference<T>, D> constructor) {
		Bundle bundle = FrameworkUtil.getBundle(clazz);
		if (bundle != null) {
			BundleContext context = bundle.getBundleContext();
			if (context != null) {
				try {
					Collection<ServiceReference<T>> serviceReferences = context.getServiceReferences(clazz, null);
					return serviceReferences.stream().map(constructor);
				} catch (InvalidSyntaxException e) {
					Errors.log().accept(e);
					return Stream.empty();
				}
			}
		}
		// it appears that we're not in an OSGi environment, switching to Osgi
		NonOsgiDS nonOsgiDS = Globals.getOrSetTo(NonOsgiDS.class, () -> {
			DpLogger.getFor(NonOsgiDS.class).log(Severity.INFO, new Throwable("OSGi search failed for " + clazz + ", failing to non-OSGi"));
			return new NonOsgiDS();
		});
		return nonOsgiDS.getServices(clazz, constructor);
	}

	/** Creates a ServiceReference around the given value, with the given properties. */
	public static <T> ServiceReference<T> mockReference(T value, String... keyValuePairs) {
		Map<String, String> map = new HashMap<>(keyValuePairs.length / 2);
		for (int i = 0; i < keyValuePairs.length / 2; ++i) {
			map.put(keyValuePairs[2 * i], keyValuePairs[2 * i + 1]);
		}
		return mockReference(value, map);
	}

	public static <T> ServiceReference<T> mockReference(T value, Map<String, String> properties) {
		return new MockReference<T>(value, properties);
	}

	private static class MockReference<T> implements ServiceReference<T> {
		final T value;
		final Map<String, String> props;

		private MockReference(T value, Map<String, String> props) {
			this.value = value;
			this.props = props;
		}

		public ServiceHandle<T> open() {
			return ServiceHandle.mock(value);
		}

		@Override
		public Object getProperty(String key) {
			return props.get(key);
		}

		@Override
		public String[] getPropertyKeys() {
			return props.keySet().toArray(new String[props.size()]);
		}

		@Override
		public Bundle getBundle() {
			Bundle bundle = FrameworkUtil.getBundle(value.getClass());
			return bundle == null ? new MockBundle(value.getClass()) : bundle;
		}

		@Override
		public Bundle[] getUsingBundles() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isAssignableTo(Bundle bundle, String className) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int compareTo(Object reference) {
			throw new UnsupportedOperationException();
		}
	}

	private static class MockBundle implements Bundle {
		private final Class<?> anchorClass;

		MockBundle(Class<?> anchorClass) {
			this.anchorClass = anchorClass;
		}

		@Override
		public URL getResource(String name) {
			return anchorClass.getResource("/" + name);
		}

		@Override
		public int compareTo(Bundle o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getState() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void start(int options) throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void start() throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void stop(int options) throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void stop() throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void update(InputStream input) throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void update() throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void uninstall() throws BundleException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Dictionary<String, String> getHeaders() {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getBundleId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getLocation() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ServiceReference<?>[] getRegisteredServices() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ServiceReference<?>[] getServicesInUse() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasPermission(Object permission) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Dictionary<String, String> getHeaders(String locale) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getSymbolicName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Enumeration<String> getEntryPaths(String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		public URL getEntry(String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getLastModified() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
			throw new UnsupportedOperationException();
		}

		@Override
		public BundleContext getBundleContext() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Version getVersion() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <A> A adapt(Class<A> type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public File getDataFile(String filename) {
			throw new UnsupportedOperationException();
		}
	}
}

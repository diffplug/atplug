/*
 * Copyright (C) 2015-2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import com.diffplug.common.base.Box;
import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Preconditions;
import com.diffplug.common.collect.ImmutableMap;
import com.diffplug.common.rx.Chit;

/** Describes an OSGi service. */
public class ServiceDescriptor<T> {
	final ServiceReference<T> ref;

	/** Creates a ServiceDescriptor wrapped around the given reference. */
	public ServiceDescriptor(ServiceReference<T> ref) {
		this.ref = ref;
	}

	@Override
	public final boolean equals(Object otherObj) {
		if (otherObj instanceof ServiceDescriptor) {
			ServiceDescriptor<?> other = (ServiceDescriptor<?>) otherObj;
			return other.ref.equals(ref);
		} else {
			return false;
		}
	}

	@Override
	public final int hashCode() {
		return ref.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String key : ref.getPropertyKeys()) {
			builder.append(key);
			builder.append("=");
			builder.append(ref.getProperty(key));
			builder.append(" ");
		}
		return builder.toString();
	}

	protected String getString(String key) {
		return (String) ref.getProperty(key);
	}

	/** Opens the service, and provides a handle for closing it when you're done. */
	public ServiceHandle<T> open() {
		if (ref instanceof MockReference) {
			return ((MockReference<T>) ref).open();
		} else {
			return ServiceHandle.Companion.open(ref);
		}
	}

	/**
	 * Opens the service, returns the service, then closes it.
	 * Should be used only for extremely light-weight services.
	 */
	public T unwrap() {
		return computeManaged(Function.identity());
	}

	/**
	 * Opens the service, computes a value using the service, then closes it.
	 * Returns the result of the computation.
	 */
	private <R> R computeManaged(Function<? super T, ? extends R> function) {
		Box.Nullable<R> result = Box.Nullable.ofNull();
		openManaged(service -> {
			result.set(function.apply(service));
		});
		return result.get();
	}

	/** Opens the service, and automatically disposes when the ear is disposed. */
	public T openManagedBy(Chit chit) {
		Preconditions.checkArgument(!chit.isDisposed());
		ServiceHandle<T> handle = open();
		chit.runWhenDisposed(handle::close);
		return handle.get();
	}

	/** Opens and closes the service safely. */
	public void openManaged(Consumer<T> consumer) {
		try (ServiceHandle<T> handle = open()) {
			consumer.accept(handle.get());
		}
	}

	/** Test harness set by `PlugHarness`. */
	static ServiceDescriptorHarness harness = null;

	static NonOsgiDS nonOsgiDS;

	/** Returns all ServiceDescriptors which are appropriate for the given class. */
	protected static <T, D extends ServiceDescriptor<T>> Stream<D> getServices(Class<T> clazz, Function<ServiceReference<T>, D> constructor) {
		if (harness != null) {
			return harness.getServices(clazz, constructor);
		}
		synchronized (ServiceDescriptor.class) {
			if (nonOsgiDS != null) {
				return nonOsgiDS.getServices(clazz, constructor);
			}
		}
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
		synchronized (ServiceDescriptor.class) {
			// if we got to here, it seems we're not in an OSGi environment, switching to non-OSGi
			if (nonOsgiDS == null) {
				nonOsgiDS = new NonOsgiDS();
			}
			return nonOsgiDS.getServices(clazz, constructor);
		}
	}

	/////////////
	// MOCKING //
	/////////////
	/** Creates a ServiceReference around the given value, with the given properties. */
	public static <Socket, T extends Socket> ServiceReference<Socket> mockReference(T value, Class<Socket> socket) {
		try {
			@SuppressWarnings("unchecked")
			DeclarativeMetadataCreator<T> creator = (DeclarativeMetadataCreator<T>) DeclarativeMetadataCreator.instantiate(socket.getClassLoader().loadClass(socket.getName() + "$MetadataCreator"));
			ImmutableMap<String, String> map = creator.metadataGenerator.apply(value);
			return mockReference(value, map);
		} catch (Exception e) {
			throw Errors.asRuntime(e);
		}
	}

	public static <T> ServiceReference<T> mockReference(T value, Map<String, String> properties) {
		return new MockReference<T>(value, properties);
	}

	/** If this service descriptor is a mock, returns the mock value. */
	protected T mockValue() {
		if (ref instanceof MockReference) {
			return ((MockReference<T>) ref).value;
		} else {
			return null;
		}
	}

	private static class MockReference<T> implements ServiceReference<T> {
		final T value;
		final Map<String, String> props;

		private MockReference(T value, Map<String, String> props) {
			this.value = value;
			this.props = props;
		}

		@Override
		public boolean equals(Object otherObj) {
			if (otherObj instanceof MockReference) {
				MockReference<?> other = (MockReference<?>) otherObj;
				return other.value.equals(value) && other.props.equals(props);
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(value, props);
		}

		public ServiceHandle<T> open() {
			return ServiceHandle.Companion.mock(value);
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

		@Override
		public Dictionary<String, Object> getProperties() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <A> A adapt(Class<A> type) {
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

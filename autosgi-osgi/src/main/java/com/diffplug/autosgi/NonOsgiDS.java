/*
 * Copyright (C) 2016-2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.autosgi;


import com.diffplug.autosgi.parsing.MarkupFormat;
import com.diffplug.common.base.Errors;
import com.diffplug.common.collect.ImmutableCollection;
import com.diffplug.common.collect.ImmutableMultimap;
import com.diffplug.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.osgi.framework.ServiceReference;

class NonOsgiDS {
	private static final String PATH_MANIFEST = "META-INF/MANIFEST.MF";
	private static final String DS_WITHIN_MANIFEST = "Service-Component";

	private final ImmutableMultimap<String, OsgiComponent> byProvides;
	private final Map<Class<?>, List<?>> instantiatedCache = new HashMap<>();

	public NonOsgiDS() {
		this(NonOsgiDS.class.getClassLoader());
	}

	public NonOsgiDS(ClassLoader classLoader) {
		ImmutableMultimap.Builder<String, OsgiComponent> builder = ImmutableMultimap.builder();
		// get all of MANIFEST.MF on the classpath
		Errors.log().run(() -> {
			Enumeration<URL> values = classLoader.getResources(PATH_MANIFEST);
			while (values.hasMoreElements()) {
				URL manifestUrl = values.nextElement();
				try (InputStream stream = manifestUrl.openStream()) {
					// parse the manifest
					Manifest manifest = new Manifest(stream);
					String services = manifest.getMainAttributes().getValue(DS_WITHIN_MANIFEST);
					if (services != null) {
						// it's got declarative services!
						for (String service : services.split(",", -1)) {
							String servicePath = service.trim();
							if (!servicePath.isEmpty()) {
								try {
									String asString = manifestUrl.toExternalForm();
									if (asString.contains("org.eclipse.core.contenttype")) {
										// causes noisy errors
										continue;
									}
									OsgiComponent component = parseComponent(asString, servicePath);
									builder.put(component.provides, component);
								} catch (Exception e) {
									Errors.log().accept(e);
								}
							}
						}
					}
				}
			}
		});
		byProvides = builder.build();
	}

	private static OsgiComponent parseComponent(String manifestUrl, String servicePath) throws IOException {
		URL serviceUrl = new URL(manifestUrl.substring(0, manifestUrl.length() - PATH_MANIFEST.length()) + servicePath);
		String serviceFileContent = Resources.toString(serviceUrl, StandardCharsets.UTF_8);
		try {
			OsgiComponent component = MarkupFormat.xml().fromString(OsgiComponent.mapping(), serviceFileContent);
			return component;
		} catch (Exception e) {
			throw new RuntimeException(serviceFileContent, e);
		}
	}

	public <T, D extends ServiceDescriptor<T>> Stream<D> getServices(Class<T> clazz, Function<ServiceReference<T>, D> constructor) {
		@SuppressWarnings("unchecked")
		List<D> list = (List<D>) instantiatedCache.computeIfAbsent(clazz, cl -> {
			ImmutableCollection<OsgiComponent> components = byProvides.get(clazz.getName());
			return components.stream().flatMap(Errors.log().wrapFunctionWithDefault((OsgiComponent component) -> {
				// this is true iff its a valid service
				Class<? extends T> implementation = (Class<? extends T>) Class.forName(component.implementation);
				T value = DeclarativeMetadataCreator.instantiate(implementation);
				ServiceReference<T> reference = ServiceDescriptor.mockReference(value, component.properties);
				return Stream.of(constructor.apply(reference));
			}, Stream.empty())).collect(Collectors.toList());
		});
		return list.stream();
	}
}

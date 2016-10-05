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

import java.util.Map;
import java.util.Objects;

import com.diffplug.closet.config.MarkupMapping;
import com.diffplug.closet.config.MarkupReader;
import com.diffplug.closet.config.MarkupWriter;
import com.diffplug.common.base.Box;
import com.diffplug.common.base.Unhandled;
import com.diffplug.common.collect.ImmutableMap;

class OsgiComponent {
	final String name;
	final String implementation;
	final String provides;
	final ImmutableMap<String, String> properties;

	OsgiComponent(String name, String implementation, String provides) {
		this(name, implementation, provides, ImmutableMap.of());
	}

	OsgiComponent(String name, String implementation, String provides, ImmutableMap<String, String> properties) {
		this.name = Objects.requireNonNull(name);
		this.implementation = Objects.requireNonNull(implementation);
		this.provides = Objects.requireNonNull(provides);
		this.properties = properties;
	}

	private static final String xml_component = "component";
	private static final String xml_name = "name";
	private static final String xml_implementation = "implementation";
	private static final String xml_class = "class";
	private static final String xml_service = "service";
	private static final String xml_provide = "provide";
	private static final String xml_interface = "interface";
	private static final String xml_property = "property";
	private static final String xml_type = "type";
	private static final String xml_string = "String";
	private static final String xml_value = "value";

	static MarkupMapping<OsgiComponent> mapping() {
		return new MarkupMapping<OsgiComponent>() {
			@Override
			public OsgiComponent read(MarkupReader reader) {
				Box.Nullable<String> name = Box.Nullable.ofNull();
				Box.Nullable<String> implementation = Box.Nullable.ofNull();
				Box.Nullable<String> provides = Box.Nullable.ofNull();
				ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
				reader.openClose(xml_component, () -> {
					name.set(reader.attributes().get(xml_name));

					while (reader.peek().isPresent()) {
						switch (reader.peek().get()) {
						case xml_implementation:
							reader.openClose(xml_implementation, () -> {
								implementation.set(reader.attributes().get(xml_class));
							});
							break;
						case xml_service:
							reader.openClose(xml_service, () -> {
								reader.openClose(xml_provide, () -> {
									provides.set(reader.attributes().get(xml_interface));
								});
							});
							break;
						case xml_property:
							reader.openClose(xml_property, () -> {
								Map<String, String> attr = reader.attributes();
								properties.put(attr.get(xml_name), attr.get(xml_value));
							});
							break;
						default:
							throw Unhandled.stringException(reader.peek().get());
						}
					}
				});
				return new OsgiComponent(name.get(), implementation.get(), provides.get(), properties.build());
			}

			@Override
			public void write(OsgiComponent object, MarkupWriter writer) {
				writer.openClose(xml_component, () -> {
					writer.attributes(xml_name, object.name);
					writer.openClose(xml_implementation, () -> {
						writer.attributes(xml_class, object.implementation);
					});
					writer.openClose(xml_service, () -> {
						writer.openClose(xml_provide, () -> {
							writer.attributes(xml_interface, object.provides);
						});
					});
					for (Map.Entry<String, String> entry : object.properties.entrySet()) {
						writer.openClose(xml_property, () -> {
							writer.attributes(xml_name, entry.getKey(), xml_type, xml_string, xml_value, entry.getValue());
						});
					}
				});
			}
		};
	}
}

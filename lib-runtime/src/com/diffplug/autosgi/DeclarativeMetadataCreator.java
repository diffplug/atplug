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

import java.util.function.Function;

import com.diffplug.closet.config.MarkupFormat;
import com.diffplug.common.collect.ImmutableMap;

public class DeclarativeMetadataCreator<T> {
	/** The socket type. */
	final Class<T> socket;
	/** Function which takes an instance of the socket type, and returns a map of its pruning properties. */
	final Function<? super T, Map<String, String>> metadataGenerator;

	public DeclarativeMetadataCreator(Class<T> socket, Function<? super T, ImmutableMap<String, String>> metadataGenerator) {
		this.socket = socket;
		this.metadataGenerator = metadataGenerator;
	}

	/** Returns the appropraite OSGI-INF for the given class. */
	public String configFor(Class<? extends T> clazz) throws InstantiationException, IllegalAccessException {
		// if annotations includes noConstructor
		//			ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
		//			Constructor<?> objDef = parent.getDeclaredConstructor();
		//			Constructor<?> intConstr = rf.newConstructorForSerialization(clazz, objDef);
		//			return clazz.cast(intConstr.newInstance());
		// else
		T instance = clazz.newInstance();
		ImmutableMap<String, String> metadata = metadataGenerator.apply(instance);
		OsgiComponent component = new OsgiComponent(clazz.getName(), clazz.getName(), socket.getName(), metadata);
		return MarkupFormat.xml().toString(OsgiComponent.mapping(), component);
	}
}

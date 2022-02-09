/*
 * Copyright (C) 2013-2022 DiffPlug
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
package com.diffplug.atplug.parsing;


import com.diffplug.common.collect.ImmutableMap;
import java.util.Map;

public interface PropWriter extends RuntimeAutoCloseable {
	void attributes(Map<String, String> map);

	default void attributes(String key, String value) {
		attributes(ImmutableMap.of(key, value));
	}

	default void attributes(String keyA, String valueA, String keyB, String valueB) {
		attributes(ImmutableMap.of(keyA, valueA, keyB, valueB));
	}

	default void attributes(String keyA, String valueA, String keyB, String valueB, String keyC, String valueC) {
		attributes(ImmutableMap.of(keyA, valueA, keyB, valueB, keyC, valueC));
	}
}

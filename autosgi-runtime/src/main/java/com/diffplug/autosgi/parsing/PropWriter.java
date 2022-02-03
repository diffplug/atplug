/*
 * Copyright (C) 2013-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

import java.util.Map;

import com.diffplug.common.collect.ImmutableMap;

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

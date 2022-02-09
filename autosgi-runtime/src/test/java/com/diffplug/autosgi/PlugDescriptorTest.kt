/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi

import com.diffplug.common.base.Converter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PlugDescriptorTest {
	@Test
	fun testRoundtrip() {
		testRoundtrip(PlugDescriptor("implementation", "provides"))
		testRoundtrip(PlugDescriptor("implementation", "provides", mapOf(Pair("prop", "value"))))
	}

	fun testRoundtrip(osgi: PlugDescriptor) {
		val converter = Converter.from(PlugDescriptor::toJson, PlugDescriptor.Companion::fromJson)
		roundtrip(converter, osgi)
	}

	/** Roundtrips the given value.  */
	private fun <T> roundtrip(converter: Converter<T, String?>, value: T) {
		val serialized = converter.convert(value)
		val parsed = converter.reverse().convert(serialized)
		val roundTripped = converter.convert(parsed)
		Assertions.assertEquals(serialized, roundTripped)
	}

	private fun <T> roundtrip(converter: Converter<T, String?>, value: T, expected: String?) {
		roundtrip(converter, value)
		Assertions.assertEquals(expected, converter.convert(value))
	}

	@Test
	fun testSerialize() {
		val converter = Converter.from(PlugDescriptor::toJson, PlugDescriptor.Companion::fromJson)
		val actual = converter.convert(PlugDescriptor("implementation", "provides"))
		val expected =
				"{\n" +
						"    \"implementation\": \"implementation\",\n" +
						"    \"provides\": \"provides\"\n" +
						"}"
		Assertions.assertEquals(expected, actual)
		testRoundtrip(PlugDescriptor("implementation", "provides"))
	}
}

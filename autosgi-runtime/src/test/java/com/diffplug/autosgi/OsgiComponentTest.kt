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

class OsgiComponentTest {
	@Test
	fun testRoundtrip() {
		testRoundtrip(OsgiComponent("name", "implementation", "provides"))
		testRoundtrip(OsgiComponent("name", "implementation", "provides", mapOf(Pair("prop", "value"))))
	}

	@Test
	fun testSerialize() {
		val converter = Converter.from(OsgiComponent::toJson, OsgiComponent.Companion::fromJson)
		val actual = converter.convert(OsgiComponent("name", "implementation", "provides"))
		val expected =
				"{\n" +
						"    \"name\": \"name\",\n" +
						"    \"implementation\": \"implementation\",\n" +
						"    \"provides\": \"provides\"\n" +
						"}"
		Assertions.assertEquals(expected, actual)
		testRoundtrip(OsgiComponent("name", "implementation", "provides"))
	}

	fun testRoundtrip(osgi: OsgiComponent) {
		val converter = Converter.from(OsgiComponent::toJson, OsgiComponent.Companion::fromJson)
		roundtrip(converter, osgi)
	}

	/** Roundtrips the given value.  */
	private fun <T> roundtrip(converter: Converter<T, String>, value: T) {
		val serialized = converter.convert(value)
		val parsed = converter.reverse().convert(serialized)
		val roundTripped = converter.convert(parsed)
		Assertions.assertEquals(serialized, roundTripped)
	}

	private fun <T> roundtrip(converter: Converter<T, String>, value: T, expected: String) {
		roundtrip(converter, value)
		Assertions.assertEquals(expected, converter.convert(value))
	}
}

/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PlugDescriptorTest {
	@Test
	fun testRoundtrip() {
		testRoundtrip(PlugDescriptor("implementation", "provides"))
		testRoundtrip(PlugDescriptor("implementation", "provides", mapOf(Pair("prop", "value"))))
	}

	private fun testRoundtrip(original: PlugDescriptor) {
		val serialized = original.toJson()
		val roundtripped = PlugDescriptor.fromJson(serialized)
		Assertions.assertEquals(original, roundtripped)
		Assertions.assertEquals(original.toJson(), roundtripped.toJson())
	}
}

/*
 * Copyright (C) 2016-2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.diffplug.autosgi.parsing.MarkupFormat;
import com.diffplug.common.base.Converter;
import com.diffplug.common.base.StringPrinter;
import com.diffplug.common.collect.ImmutableMap;
import com.diffplug.testcloset.ConfigTestUtil;

public class OsgiComponentTest {
	@Test
	public void testRoundtrip() {
		testRoundtrip(new OsgiComponent("name", "implementation", "provides"));
		testRoundtrip(new OsgiComponent("name", "implementation", "provides", ImmutableMap.of("prop", "value")));
	}

	public void testRoundtrip(OsgiComponent osgi) {
		Converter<OsgiComponent, String> converter = MarkupFormat.xml().asConverter(OsgiComponent.mapping());
		ConfigTestUtil.roundtrip(converter, osgi);
	}

	@Test
	public void testSerialize() {
		Converter<OsgiComponent, String> converter = MarkupFormat.xml().asConverter(OsgiComponent.mapping());
		String actual = converter.convert(new OsgiComponent("name", "implementation", "provides"));
		String expected = StringPrinter.buildStringFromLines(
				"<component name=\"name\">",
				"\t<implementation class=\"implementation\"></implementation>",
				"\t<service>",
				"\t\t<provide interface=\"provides\"></provide>",
				"\t</service>",
				"</component>").trim();
		Assertions.assertEquals(expected, actual);
		testRoundtrip(new OsgiComponent("name", "implementation", "provides"));
	}
}

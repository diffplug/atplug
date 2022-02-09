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
package com.diffplug.atplug;


import com.diffplug.atplug.parsing.MarkupFormat;
import com.diffplug.common.base.Converter;
import com.diffplug.common.base.StringPrinter;
import com.diffplug.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OsgiComponentTest {
	@Test
	public void testRoundtrip() {
		testRoundtrip(new OsgiComponent("name", "implementation", "provides"));
		testRoundtrip(new OsgiComponent("name", "implementation", "provides", ImmutableMap.of("prop", "value")));
	}

	public void testRoundtrip(OsgiComponent osgi) {
		Converter<OsgiComponent, String> converter = MarkupFormat.xml().asConverter(OsgiComponent.mapping());
		roundtrip(converter, osgi);
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

	/** Roundtrips the given value. */
	private static <T> void roundtrip(Converter<T, String> converter, T value) {
		String serialized = converter.convert(value);
		T parsed = converter.reverse().convert(serialized);
		String roundTripped = converter.convert(parsed);
		Assertions.assertEquals(serialized, roundTripped);
	}

	private static <T> void roundtrip(Converter<T, String> converter, T value, String expected) {
		roundtrip(converter, value);
		Assertions.assertEquals(expected, converter.convert(value));
	}
}

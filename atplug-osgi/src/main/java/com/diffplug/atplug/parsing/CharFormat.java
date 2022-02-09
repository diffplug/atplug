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
package com.diffplug.atplug.parsing;


import com.diffplug.common.base.Converter;
import com.diffplug.common.base.Errors;
import com.diffplug.common.base.StringPrinter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

/** Defines a format based on character streams. */
public interface CharFormat<StructuredReader extends AutoCloseable, StructuredWriter extends AutoCloseable> {
	StructuredReader reader(Reader reader);

	StructuredWriter writer(Writer writer);

	/** Converts the Mapping.InPlace to a string. */
	default String toString(Mapping.InPlace<StructuredReader, StructuredWriter> mappable) {
		return StringPrinter.buildString(Errors.rethrow().wrap(printer -> {
			try (StructuredWriter writer = writer(printer.toWriter())) {
				mappable.write(writer);
			}
		}));
	}

	/** Converts the value into a string using the given Mapping. */
	default <T> String toString(Mapping<T, StructuredReader, StructuredWriter> mapping, T value) {
		return StringPrinter.buildString(Errors.rethrow().wrap(printer -> {
			try (StructuredWriter writer = writer(printer.toWriter())) {
				mapping.write(value, writer);
			}
		}));
	}

	/** Sets the Mapping.InPlace using the given string. */
	default void fromString(Mapping.InPlace<StructuredReader, StructuredWriter> inPlace, String source) {
		Errors.rethrow().run(() -> {
			try (StructuredReader reader = reader(new StringReader(source))) {
				inPlace.read(reader);
			}
		});
	}

	/** Parses an object from the given string using the given mapping. */
	default <T> T fromString(Mapping<T, StructuredReader, StructuredWriter> mapping, String source) {
		return Errors.rethrow().get(() -> {
			try (StructuredReader reader = reader(new StringReader(source))) {
				return mapping.read(reader);
			}
		});
	}

	/** Returns a converter which can convert an object to and from its string format. */
	default <T> Converter<T, String> asConverter(Mapping<T, StructuredReader, StructuredWriter> mapping) {
		return Converter.from(
				(T value) -> toString(mapping, value),
				(String string) -> fromString(mapping, string));
	}
}

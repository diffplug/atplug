/*
 * Copyright (C) 2017-2022 DiffPlug
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
package com.diffplug.autosgi.parsing;


import com.diffplug.common.base.Errors;
import java.io.Writer;
import java.util.function.BiFunction;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public final class MarkupFormatImp implements MarkupFormat {
	final BiFunction<XMLStreamWriter, java.io.Writer, MarkupWriter> createWriter;
	final BiFunction<XMLStreamReader, java.io.Reader, MarkupReader> createReader;

	public MarkupFormatImp(BiFunction<XMLStreamReader, java.io.Reader, MarkupReader> createReader, BiFunction<XMLStreamWriter, Writer, MarkupWriter> createWriter) {
		this.createReader = createReader;
		this.createWriter = createWriter;
	}

	@Override
	public MarkupReader reader(java.io.Reader reader) {
		return Errors.rethrow().get(() -> {
			XMLStreamReader xmlReader = XMLInputFactory.newFactory().createXMLStreamReader(reader);
			return createReader.apply(xmlReader, reader);
		});
	}

	@Override
	public MarkupWriter writer(java.io.Writer writer) {
		return Errors.rethrow().get(() -> {
			XMLStreamWriter xmlWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
			return createWriter.apply(xmlWriter, writer);
		});
	}
}

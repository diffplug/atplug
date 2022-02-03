/*
 * Copyright (C) 2017-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

import java.io.Writer;
import java.util.function.BiFunction;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.diffplug.common.base.Errors;

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

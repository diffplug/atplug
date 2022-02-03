/*
 * Copyright (C) 2016-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** Utilities for conducting IO on XML data. */
public class XmlIO {
	public static MarkupWriter writer(File file) throws Exception {
		FileOutputStream stream = new FileOutputStream(file);
		return writer(new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8)));
	}

	public static MarkupWriter writer(Writer writer) throws Exception {
		XMLStreamWriter xmlWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
		return new XmlStaxWriter.Pretty(xmlWriter, writer);
	}

	public static MarkupReader reader(File file) throws Exception {
		FileInputStream stream = new FileInputStream(file);
		return reader(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
	}

	public static MarkupReader reader(String content) throws Exception {
		return reader(new StringReader(content));
	}

	public static MarkupReader reader(Reader reader) throws Exception {
		XMLStreamReader xmlReader = XMLInputFactory.newFactory().createXMLStreamReader(reader);
		return new XmlStaxReader(xmlReader, reader);
	}
}

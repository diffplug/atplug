/*
 * Copyright (C) 2016-2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLStreamWriter;

import com.diffplug.common.base.Errors;

/** A TreeWriter.Markup implementation using an XMLStreamWriter. */
public class XmlStaxWriter implements MarkupWriter {
	/** The StAX reader underlying our reader. */
	protected final XMLStreamWriter writer;
	/** A resource which will be closed after this is closed (XMLEventReader doesn't close its underlying stream). */
	protected final AutoCloseable closeWith;

	protected final Deque<String> elementStack = new ArrayDeque<String>();
	private String pendingAttributes;

	/**
	 * Opens a reader wrapped around the given XMLEventReader,
	 * which will close `closeWith` after the reader is closed.
	 */
	public XmlStaxWriter(XMLStreamWriter writer, AutoCloseable closeWith) {
		this.writer = Objects.requireNonNull(writer);
		this.closeWith = Objects.requireNonNull(closeWith);
	}

	@Override
	public void open(String name) {
		Errors.rethrow().run(() -> {
			writer.writeStartElement(name);
			elementStack.push(name);
		});
		// if the user writes attributes, they will be written to this element
		pendingAttributes = name;
	}

	@Override
	public void close(String name) {
		if (elementStack.isEmpty()) {
			throw new IllegalArgumentException("Expected to close " + name + " but the root element has been closed.");
		}
		String popped = elementStack.pop();
		if (!popped.equals(name)) {
			throw new IllegalArgumentException("Expected to close " + popped + " but was " + name);
		}
		Errors.rethrow().run(() -> {
			writer.writeEndElement();
		});
		// it's too late to write attributes
		pendingAttributes = null;
	}

	@Override
	public void attributes(Map<String, String> attributes) {
		if (pendingAttributes == null || !pendingAttributes.equals(elementStack.peek())) {
			throw new IllegalArgumentException("Must write attributes right after opening!");
		}
		Errors.rethrow().run(() -> {
			for (Map.Entry<String, String> entry : attributes.entrySet()) {
				writer.writeAttribute(entry.getKey(), xmlAttrEncode(entry.getValue()));
			}
		});
		pendingAttributes = null;
	}

	@Override
	public void content(String content) {
		Errors.rethrow().run(() -> {
			writer.writeCharacters(content);
		});
		pendingAttributes = null;
	}

	/** Writes a comment into the file. */
	public void comment(String comment) {
		Errors.rethrow().run(() -> {
			writer.writeComment(comment);
		});
		pendingAttributes = null;
	}

	@Override
	public void close() {
		Errors.rethrow().run(() -> {
			writer.close();
			closeWith.close();
		});
	}

	/** An XmlStaxWriter which tries to make the XML a little more friendly-looking. */
	public static class Pretty extends XmlStaxWriter {

		public Pretty(XMLStreamWriter writer, AutoCloseable closeWith) {
			super(writer, closeWith);
		}

		private final Deque<Boolean> hasChildren = new ArrayDeque<>();

		@Override
		public void open(String name) {
			Errors.rethrow().run(() -> {
				if (!elementStack.isEmpty()) {
					writer.writeCharacters("\n");
					for (int i = 0; i < hasChildren.size(); ++i) {
						writer.writeCharacters("\t");
					}
					// the old one must have children
					hasChildren.pop();
					hasChildren.push(true);
				}
				// but we don't
				hasChildren.push(false);
				super.open(name);
			});
		}

		@Override
		public void close(String name) {
			Errors.rethrow().run(() -> {
				if (hasChildren.pop()) {
					writer.writeCharacters("\n");
					for (int i = 0; i < hasChildren.size(); ++i) {
						writer.writeCharacters("\t");
					}
				}
				super.close(name);
			});
		}

		@Override
		public void content(String content) {
			if (content.contains("\n")) {
				super.content("\n" + content);
			} else {
				super.content(content);
			}
		}
	}

	private static final String NEWLINE_CONSTANT = "&#10;";

	static String xmlAttrEncode(String toEncode) {
		return toEncode.replace("\n", NEWLINE_CONSTANT);
	}

	static String xmlAttrDecode(String toDecode) {
		return toDecode.replace(NEWLINE_CONSTANT, "\n");
	}
}

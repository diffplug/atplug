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


import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Unhandled;
import com.diffplug.common.collect.ImmutableMap;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class XmlStaxReader implements MarkupReader {
	/** The StAX reader underlying our reader. */
	final XMLStreamReader reader;
	/** A resource which will be closed after this is closed (XMLStreamReader doesn't close its underlying stream). */
	final AutoCloseable closeWith;
	/** Events waiting to be parsed. */
	final Queue<Event> pending = new ArrayDeque<>(2);
	/** Reusable buffer for building content entries. */
	final StringBuilder characters = new StringBuilder();
	/** The attributes for the just-opened tag. */
	ImmutableMap<String, String> attributes;

	/**
	 * Opens a reader wrapped around the given XMLStreamReader,
	 * which will close `closeWith` after the reader is closed.
	 */
	public XmlStaxReader(XMLStreamReader reader, AutoCloseable closeWith) {
		this.reader = Objects.requireNonNull(reader);
		this.closeWith = Objects.requireNonNull(closeWith);
	}

	/** Populates pending if it is empty. */
	private void populatePending() {
		while (pending.isEmpty()) {
			try {
				Event tag = nextEvent();
				if (tag == null) {
					continue;
				} else {
					// if we found text, add it
					if (characters.length() > 0) {
						pending.add(new Event(characters.toString(), Type.CHARACTERS));
						characters.setLength(0);
					}
					pending.add(tag);
				}
			} catch (XMLStreamException e) {
				throw Errors.asRuntime(e);
			}
		}
	}

	/**
	 * Reads the next event from the underlying reader,
	 * and returns it as an evner if possible, else returns
	 * null (for comments, DTD, etc). 
	 */
	private Event nextEvent() throws XMLStreamException {
		int nextEventType = reader.next();
		switch (nextEventType) {
		case XMLStreamConstants.START_ELEMENT: {
			if (reader.getAttributeCount() == 0) {
				return new Event(reader.getLocalName(), Type.OPEN);
			} else {
				ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
				for (int i = 0; i < reader.getAttributeCount(); ++i) {
					builder.put(reader.getAttributeLocalName(i), XmlStaxWriter.xmlAttrDecode(reader.getAttributeValue(i)));
				}
				return new Event(reader.getLocalName(), Type.OPEN, builder.build());
			}
		}
		case XMLStreamConstants.END_ELEMENT: {
			return new Event(reader.getLocalName(), Type.CLOSE);
		}
		case XMLStreamConstants.CDATA:
		case XMLStreamConstants.CHARACTERS: {
			int start = reader.getTextStart();
			int end = reader.getTextLength();
			characters.append(reader.getTextCharacters(), start, end);
			return null;
		}
		default:
			return null;
		}
	}

	@Override
	public Optional<String> peek() {
		populatePending();
		Event event = pending.peek();
		if (event.type == Type.OPEN) {
			return Optional.of(event.value);
		} else if (event.type == Type.CLOSE) {
			return Optional.empty();
		} else {
			// it was a character event, so we've gotta keep looking...
			event = pending.stream().filter(e -> e.type.isTag()).findFirst().get();
			return event.type == Type.OPEN ? Optional.of(event.value) : Optional.empty();
		}
	}

	private Event findOpenOrClose(Type type, String name) {
		populatePending();
		Event event = pending.poll();
		if (event.type == Type.CHARACTERS) {
			// allow to skip one CHARACTERS
			event = pending.poll();
		}
		if (event.type == type && event.value.equals(name)) {
			return event;
		} else {
			throw new IllegalArgumentException("Expected " + type + " " + name + ", but was " + event.type + " " + event.value);
		}
	}

	@Override
	public void open(String name) {
		Event tag = findOpenOrClose(Type.OPEN, name);
		attributes = tag.attributes;
	}

	@Override
	public void close(String name) {
		findOpenOrClose(Type.CLOSE, name);
		attributes = null;
	}

	@Override
	public Map<String, String> attributes() {
		return Objects.requireNonNull(attributes);
	}

	@Override
	public String content() {
		populatePending();
		Event tag = pending.peek();
		if (tag.type == Type.CHARACTERS) {
			pending.poll();
			return tag.value;
		} else {
			return "";
		}
	}

	@Override
	public void close() {
		try {
			reader.close();
			closeWith.close();
		} catch (Exception e) {
			throw Errors.asRuntime(e);
		}
	}

	/** Specifies what sort of event we're looking at. */
	private enum Type {
		OPEN, CLOSE, CHARACTERS;

		public boolean isTag() {
			return this != CHARACTERS;
		}
	}

	/**
	 * Represents one event in an XmlFile. XmlReader expands
	 * EMPTY events into OPEN and CLOSE events, and XmlWriter
	 * condenses OPEN and CLOSE events with no children into
	 * EMPTY events.
	 */
	private static class Event implements Cloneable {
		final String value;
		final Type type;
		final ImmutableMap<String, String> attributes;

		Event(String value, Type type, ImmutableMap<String, String> attributes) {
			this.value = value;
			this.type = type;
			this.attributes = attributes;
		}

		Event(String elementName, Type type) {
			this(elementName, type, ImmutableMap.of());
		}

		@Override
		public String toString() {
			switch (type) {
			case OPEN:
				return "<" + value + " " + attributes.toString() + ">";
			case CLOSE:
				return "<" + value + "/>";
			case CHARACTERS:
				return "CHARACTERS[" + value.trim() + "]";
			default:
				throw Unhandled.enumException(type);
			}
		}
	}

	public static class Pretty extends XmlStaxReader {

		public Pretty(XMLStreamReader reader, AutoCloseable closeWith) {
			super(reader, closeWith);
		}

		@Override
		public String content() {
			String read = super.content();
			if (read.trim().isEmpty()) {
				return "";
			} else if (read.indexOf('\n') >= 2) {
				int firstCut = read.indexOf('\n');
				int lastCut = read.lastIndexOf('\n');
				if (read.substring(0, firstCut).trim().isEmpty() && read.substring(lastCut).trim().isEmpty()) {
					// if we can trim our content, then trim it
					return read.substring(firstCut + 1, lastCut);
				} else {
					return read;
				}
			} else {
				return read;
			}
		}
	}
}

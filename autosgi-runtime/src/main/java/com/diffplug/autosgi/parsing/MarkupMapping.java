/*
 * Copyright (C) 2016-2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("FunctionalInterfaceClash")
public interface MarkupMapping<T> extends Mapping<T, MarkupReader, MarkupWriter> {
	/** Reads every remaining open element in this block and passes it to onAdd. */
	default void readCollection(Consumer<? super T> onAdd, MarkupReader reader) {
		readCollection(r -> r.peek().isPresent(), onAdd, reader);
	}

	/** Creates a Mapping using an external function for getting a Mapping.InPlace. */
	static <T> MarkupMapping<T> fromInPlace(Supplier<T> factory, Function<T, InPlace> mappingFactory) {
		return wrap(Mapping.fromInPlace(factory, mappingFactory));
	}

	/** Creates a Mapping for objects which implement Mapping.InPlace. */
	static <T extends InPlace> MarkupMapping<T> fromInPlace(Supplier<T> factory) {
		return fromInPlace(factory, t -> t);
	}

	/** Copies the given source object using this mapping. */
	default T copy(T source) {
		MarkupFormat format = MarkupFormat.xmlCompact();
		String sourceAsString = format.toString(this, source);
		return format.fromString(this, sourceAsString);
	}

	interface InPlace extends Mapping.InPlace<MarkupReader, MarkupWriter> {
		/** Serializes source, and deserializes into dest. */
		static <T extends InPlace> void set(T dest, T source) {
			String sourceAsString = MarkupFormat.xmlCompact().toString(source);
			MarkupFormat.xmlCompact().fromString(dest, sourceAsString);
		}

		/** Copies source, using creator to create the instance. */
		static <T extends InPlace> T copy(T src, Supplier<T> creator) {
			T copy = creator.get();
			set(copy, src);
			return copy;
		}

		/** Converts a mapping whose generic type could be a MarkupMapping into a MarkupMapping. */
		static InPlace wrap(Mapping.InPlace<? extends MarkupReader, ? extends MarkupWriter> compatible) {
			@SuppressWarnings("unchecked")
			Mapping.InPlace<MarkupReader, MarkupWriter> mapping = (Mapping.InPlace<MarkupReader, MarkupWriter>) compatible;
			return new InPlace() {
				@Override
				public void read(MarkupReader reader) {
					mapping.read(reader);
				}

				@Override
				public void write(MarkupWriter writer) {
					mapping.write(writer);
				}
			};
		}

		static InPlace doNothing() {
			return new InPlace() {
				@Override
				public void read(MarkupReader reader) {}

				@Override
				public void write(MarkupWriter writer) {}
			};
		}
	}

	/** Converts a mapping whose generic type could be a MarkupMapping into a MarkupMapping. */
	static <T> MarkupMapping<T> wrap(Mapping<T, ? extends MarkupReader, ? extends MarkupWriter> compatible) {
		@SuppressWarnings("unchecked")
		Mapping<T, MarkupReader, MarkupWriter> mapping = (Mapping<T, MarkupReader, MarkupWriter>) compatible;
		return new MarkupMapping<T>() {
			@Override
			public T read(MarkupReader reader) {
				return mapping.read(reader);
			}

			@Override
			public void write(T object, MarkupWriter writer) {
				mapping.write(object, writer);
			}
		};
	}

	static <T> MarkupMapping<T> methodMapper(Function<MarkupReader, T> readMapper, BiConsumer<T, MarkupWriter> writeMapper) {
		return wrap(Mapping.methodMapper(readMapper, writeMapper));
	}

	/** Returns a mapping which stores strings in the content of the writer. */
	static MarkupMapping<String> toContent() {
		return methodMapper(MarkupReader::content, (value, writer) -> writer.content(value));
	}

	/** Returns a mapping which stores strings in the content of the writer. */
	static MarkupMapping<Map<String, String>> toAttributes() {
		return methodMapper(MarkupReader::attributes, (value, writer) -> writer.attributes(value));
	}
}

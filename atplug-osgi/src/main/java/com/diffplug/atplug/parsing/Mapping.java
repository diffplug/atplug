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


import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Represents a mapping between an object and a reader / writer pair. */
public interface Mapping<T, Reader, Writer> {
	/** Reads an object from the reader. */
	T read(Reader reader);

	/** Writes an object to the writer. */
	void write(T object, Writer writer);

	/** So long as moreAvailable returns true, this mapping will call read and pass the result to onAdd. */
	default void readCollection(Predicate<Reader> moreAvailable, Consumer<? super T> onAdd, Reader reader) {
		while (moreAvailable.test(reader)) {
			onAdd.accept(read(reader));
		}
	}

	interface InPlace<Reader, Writer> {
		/** Reads itself from the reader. */
		void read(Reader reader);

		/** Writes itself to the writer. */
		void write(Writer writer);
	}

	/** Creates a Mapping using an external function for getting a Mapping.InPlace. */
	static <T, Reader, Writer> Mapping<T, Reader, Writer> fromInPlace(Supplier<T> factory, Function<T, ? extends Mapping.InPlace<Reader, Writer>> mappingFactory) {
		return new Mapping<T, Reader, Writer>() {
			@Override
			public T read(Reader reader) {
				T value = factory.get();
				mappingFactory.apply(value).read(reader);
				return value;
			}

			@Override
			public void write(T object, Writer writer) {
				mappingFactory.apply(object).write(writer);
			}
		};
	}

	static <T, Reader, Writer> Mapping<T, Reader, Writer> methodMapper(Function<Reader, T> readMapper, BiConsumer<T, Writer> writeMapper) {
		return new Mapping<T, Reader, Writer>() {
			@Override
			public T read(Reader reader) {
				return readMapper.apply(reader);
			}

			@Override
			public void write(T object, Writer writer) {
				writeMapper.accept(object, writer);
			}
		};
	}
}

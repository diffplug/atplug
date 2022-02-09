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

/** A format which defines a mapping from character streams to typed-streams. */
public interface MarkupFormat extends CharFormat<MarkupReader, MarkupWriter> {
	/** Returns an XML-based MarkupFormat. */
	static MarkupFormat xml() {
		return new MarkupFormatImp(XmlStaxReader.Pretty::new, XmlStaxWriter.Pretty::new);
	}

	/** Returns a compact XML-based MarkupFormat. */
	static MarkupFormat xmlCompact() {
		return new MarkupFormatImp(XmlStaxReader::new, XmlStaxWriter::new);
	}
}

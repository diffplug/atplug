/*
 * Copyright (C) 2016-2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

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

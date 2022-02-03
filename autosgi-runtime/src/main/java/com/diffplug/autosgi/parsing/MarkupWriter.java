/*
 * Copyright (C) 2015-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

/** Interface which supports hierarchical markup, including trees. */
public interface MarkupWriter extends TreeWriter, PropWriter, RuntimeAutoCloseable {
	void content(String content);
}

/*
 * Copyright (C) 2015-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

/** AutoCloseable which doesn't force a catch when used in try-with-resources. */
public interface RuntimeAutoCloseable extends AutoCloseable {
	@Override
	void close() throws RuntimeException;

}

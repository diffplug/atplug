/*
 * Copyright (C) 2013-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi.parsing;

public interface TreeWriter extends PropWriter, AutoCloseable {
	void open(String name);

	void close(String name);

	default void openClose(String name, Runnable runnable) {
		open(name);
		runnable.run();
		close(name);
	}
}

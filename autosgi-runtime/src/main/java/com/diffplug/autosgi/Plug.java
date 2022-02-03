/*
 * Copyright (C) 2016-2018 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.autosgi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which signals that this
 * class implements the given socket.
 *
 * We should name this autOSGi
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Plug {
	/** Socket type which this plug implements. */
	Class<?> value();
}

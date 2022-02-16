/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import kotlin.reflect.KClass

/** Annotation which signals that this class implements the given socket. */
@kotlin.annotation.Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class Plug(
		/** Socket type which this plug implements. */
		val value: KClass<*>
)

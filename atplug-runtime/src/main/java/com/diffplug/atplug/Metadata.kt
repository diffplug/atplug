/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

/** Marks that a method is used to generate metadata, and should therefore return a constant. */
@Retention(AnnotationRetention.SOURCE)
@Target(
		AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Metadata

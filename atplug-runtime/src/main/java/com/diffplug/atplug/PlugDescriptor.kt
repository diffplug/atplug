/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PlugDescriptor(
		val implementation: String,
		val provides: String,
		val properties: Map<String, String> = mapOf()
) {
	override fun toString(): String {
		return "$provides by $implementation with $properties"
	}

	fun toJson() = Json { prettyPrint = true }.encodeToString(this)

	companion object {
		fun fromJson(string: String) = Json.decodeFromString<PlugDescriptor>(string)
	}
}

/*
 * Copyright (C) 2022 DiffPlug
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
package com.diffplug.atplug.tooling

import com.diffplug.atplug.tooling.gradle.JavaExecable
import java.io.File
import java.util.*

/** [PlugGenerator.PlugGenerator] in a [JavaExecable] form. */
class PlugGeneratorJavaExecable(
		plugToSocket: Map<String, String>,
		toSearch: List<File>,
		toLinkAgainst: Set<File>
) : JavaExecable {
	// inputs
	var plugToSocket: Map<String, String> = LinkedHashMap(plugToSocket)
	var toSearch: List<File> = ArrayList(toSearch)
	var toLinkAgainst: Set<File> = LinkedHashSet(toLinkAgainst)

	// outputs
	@JvmField var atplugInf: SortedMap<String, String>? = null

	override fun run() {
		val metadataGen = PlugGenerator(plugToSocket, toSearch, toLinkAgainst)
		atplugInf = metadataGen.atplugInf
	}
}

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
package com.diffplug.atplug.tooling;


import com.diffplug.atplug.tooling.gradle.JavaExecable;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * {@link PlugGenerator#PlugGenerator(List, Set)} in a {@link JavaExecable} form.
 */
public class PlugGeneratorJavaExecable implements JavaExecable {
	// inputs
	List<File> toSearch;
	Set<File> toLinkAgainst;

	public PlugGeneratorJavaExecable(List<File> toSearch, Set<File> toLinkAgainst) {
		this.toSearch = new ArrayList<>(toSearch);
		this.toLinkAgainst = new LinkedHashSet<>(toLinkAgainst);
	}

	// outputs
	SortedMap<String, String> atplugInf;

	public SortedMap<String, String> getAtplugInf() {
		return atplugInf;
	}

	@Override
	public void run() {
		PlugGenerator metadataGen = new PlugGenerator(toSearch, toLinkAgainst);
		atplugInf = metadataGen.atplugInf;
	}
}

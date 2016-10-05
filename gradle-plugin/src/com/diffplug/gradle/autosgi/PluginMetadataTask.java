/*
 * Copyright 2016 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.autosgi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import com.diffplug.common.base.Unhandled;
import com.diffplug.gradle.FileMisc;
import com.diffplug.gradle.JavaExecable;

public class PluginMetadataTask extends DefaultTask {
	static final String TASK_NAME = "pluginMetadata";

	private File toSearch;
	private Set<File> toLinkAgainst;
	private File osgiInfFolder;
	private JavaExecable.Mode jvmMode;

	public void init(File toSearch, Set<File> toLinkAgainst, File osgiInfFolder, JavaExecable.Mode jvmMode) {
		// store the arguments
		this.toSearch = toSearch;
		this.toLinkAgainst = toLinkAgainst;
		this.osgiInfFolder = osgiInfFolder;
		this.jvmMode = jvmMode;

		// set task input/output data
		getInputs().dir(toSearch);
		toLinkAgainst.forEach(getInputs()::file);
		getOutputs().dir(osgiInfFolder);
	}

	@TaskAction
	public void build() throws Throwable {
		// generate the metadata
		Map<String, String> result = generate();

		// clean out the osgiInf folder, and put the map's content into the folder
		FileMisc.cleanDir(osgiInfFolder);
		for (Map.Entry<String, String> entry : result.entrySet()) {
			Files.write(osgiInfFolder.toPath().resolve(entry.getKey() + PluginMetadataPlugin.DOT_XML), entry.getValue().getBytes(StandardCharsets.UTF_8));
		}
	}

	private Map<String, String> generate() throws Throwable {
		switch (jvmMode) {
		case INTERNAL:
			return PluginMetadataGen.generate(toSearch, toLinkAgainst);
		case EXTERNAL:
			PluginMetadataGen.External input = new PluginMetadataGen.External(toSearch, toLinkAgainst);
			PluginMetadataGen.External result = JavaExecable.exec(getProject(), input);
			return result.osgiInf;
		default:
			throw Unhandled.enumException(jvmMode);
		}
	}
}

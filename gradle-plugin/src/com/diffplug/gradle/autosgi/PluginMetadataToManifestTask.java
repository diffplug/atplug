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

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

import com.diffplug.gradle.FileMisc;

public class PluginMetadataToManifestTask extends DefaultTask {
	static final String TASK_NAME = "pluginMetadataToManifest";

	private File osgiInfFolder;
	private Jar jarTask;

	public void init(File osgiInfFolder, Jar jarTask) {
		// store the arguments
		this.osgiInfFolder = osgiInfFolder;
		this.jarTask = jarTask;
	}

	@TaskAction
	public void build() throws Throwable {
		StringBuilder builder = new StringBuilder();
		for (File file : FileMisc.list(osgiInfFolder)) {
			if (file.getName().endsWith(PluginMetadataPlugin.DOT_XML)) {
				builder.append(PluginMetadataPlugin.OSGI_INF + file.getName());
				builder.append(",");
			}
		}
		if (builder.length() > 0) {
			builder.setLength(builder.length() - 1);
			String serviceComponent = builder.toString();
			jarTask.getManifest().getEffectiveManifest().getAttributes().put(PluginMetadataPlugin.SERVICE_COMPONENT, serviceComponent);
		}
	}
}

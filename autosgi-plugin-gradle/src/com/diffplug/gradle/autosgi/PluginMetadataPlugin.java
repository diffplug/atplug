/*
 * Copyright 2018 DiffPlug
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
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;

import com.diffplug.gradle.ProjectPlugin;

/**
 * Plugin which creates OSGI-INF metadata for a project.
 * <p>
 * {@code
 * diffplugPluginMetadata {
 *     plugin 'com.diffplug.fs.RightClickFile'
 * }
 * }
 * <p>
 * will create metadata for all implementations of com.diffplug.fs.RightClickFile.
 */
public class PluginMetadataPlugin extends ProjectPlugin {
	static final String SERVICE_COMPONENT = "Service-Component";
	static final String OSGI_INF = "OSGI-INF/";
	static final String DOT_XML = ".xml";

	@Override
	protected void applyOnce(Project project) {
		PluginMetadataExtension extension = project.getExtensions().create(PluginMetadataExtension.NAME, PluginMetadataExtension.class);
		project.afterEvaluate(p -> {
			// get the classes we're compiling
			JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
			SourceSet main = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
			File classesDir = main.getOutput().getClassesDirs().getSingleFile();

			// and the dependencies they need to run
			ProjectPlugin.getPlugin(project, JavaPlugin.class);
			Set<File> toLink = project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).getFiles();

			// create the metadata
			File osgiInf = project.file(OSGI_INF);
			PluginMetadataTask pluginMetadataTask = project.getTasks().create(PluginMetadataTask.TASK_NAME, PluginMetadataTask.class);
			pluginMetadataTask.classesFolder = classesDir;
			pluginMetadataTask.jarsToLinkAgainst = toLink;
			pluginMetadataTask.jvmMode = extension.mode;
			pluginMetadataTask.osgiInfFolder = osgiInf;

			// compile -> generateOsgiMetadata -> processResources
			pluginMetadataTask.dependsOn(project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME));
			project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).dependsOn(pluginMetadataTask);

			// put it into the manifest
			Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
			PluginMetadataToManifestTask pluginMetadataToManifestTask = project.getTasks().create(PluginMetadataToManifestTask.TASK_NAME, PluginMetadataToManifestTask.class);
			pluginMetadataToManifestTask.init(osgiInf, jarTask);

			pluginMetadataToManifestTask.dependsOn(pluginMetadataTask);
			jarTask.dependsOn(pluginMetadataToManifestTask);
		});
	}
}

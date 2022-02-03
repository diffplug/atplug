/*
 * Copyright (C) 2020-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.plug.generate.gradle;


import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

/**
 * `plugGenerate` task uses `@Plug` to generate files
 * in `src/main/resources/OSGI-INF` as a dependency of
 * `processResources`.
 */
public class PlugPlugin implements Plugin<Project> {
	static final String GENERATE = "plugGenerate";

	static final String SERVICE_COMPONENT = "Service-Component";
	static final String DOT_XML = ".xml";
	static final String OSGI_INF = "OSGI-INF/";

	@Override
	public void apply(Project project) {
		PlugExtension extension = project.getExtensions().create(PlugExtension.NAME, PlugExtension.class);
		// get the classes we're compiling
		project.getPlugins().apply(JavaPlugin.class);
		JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
		SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		// jar --dependsOn--> plugGenerate
		TaskProvider<PlugGenerateTask> generateTask = project.getTasks().register(GENERATE, PlugGenerateTask.class, task -> {
			task.setClassesFolders(main.getOutput().getClassesDirs());
			task.getJarsToLinkAgainst().setFrom(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
			task.resourcesFolder = main.getResources().getSourceDirectories().getSingleFile();
			// dep on java
			for (String taskName : Arrays.asList(
					"compileJava",
					"compileKotlin")) {
				try {
					task.dependsOn(project.getTasks().named(taskName));
				} catch (UnknownTaskException e) {
					// not a problem
				}
			}
		});
		project.getTasks().named(JavaPlugin.JAR_TASK_NAME).configure(jarTaskUntyped -> {
			Jar jarTask = (Jar) jarTaskUntyped;
			PlugGenerateTask metadataTask = generateTask.get();
			jarTask.getInputs().dir(metadataTask.getOsgiInfFolder());
			jarTask.doFirst("Set " + PlugPlugin.SERVICE_COMPONENT + " header", new SetServiceComponentHeader(metadataTask.getOsgiInfFolder()));
		});
		project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).configure(t -> t.dependsOn(generateTask));
	}

	static class SetServiceComponentHeader implements Serializable, Action<Task> {
		private final File osgiInfFolder;

		SetServiceComponentHeader(File osgiInfFolder) {
			this.osgiInfFolder = osgiInfFolder;
		}

		@Override
		public void execute(Task task) {
			String serviceComponents = PlugGenerateTask.serviceComponents(osgiInfFolder);
			Jar jarTask = (Jar) task;
			if (serviceComponents == null) {
				jarTask.getManifest().getAttributes().remove(PlugPlugin.SERVICE_COMPONENT);
			} else {
				jarTask.getManifest().getAttributes().put(PlugPlugin.SERVICE_COMPONENT, serviceComponents);
			}
		}
	}
}

/*
 * Copyright (C) 2020-2022 DiffPlug
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
package com.diffplug.atplug.tooling.gradle;

import com.diffplug.atplug.tooling.PlugGeneratorJavaExecable;
import com.diffplug.gradle.FileMisc;
import com.diffplug.gradle.JRE;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

public abstract class PlugGenerateTask extends DefaultTask {
	public PlugGenerateTask() {
		this.getOutputs().upToDateWhen(unused -> {
			Manifest manifest = loadManifest();
			String componentsCmd = atplugComponents();
			String componentsActual = manifest.getMainAttributes().getValue(PlugPlugin.SERVICE_COMPONENT);
			return Objects.equals(componentsActual, componentsCmd);
		});

		JavaToolchainSpec spec = getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
		JavaToolchainService service = getProject().getExtensions().getByType(JavaToolchainService.class);
		getLauncher().set(service.launcherFor(spec));
	}

	@Nested
	@Optional
	abstract Property<JavaLauncher> getLauncher();

	@Inject
	abstract public WorkerExecutor getWorkerExecutor();

	@Inject
	abstract public ObjectFactory getFS();

	@Classpath
	@InputFiles
	public abstract ConfigurableFileCollection getJarsToLinkAgainst();

	public File resourcesFolder;

	@Internal
	public File getResourcesFolder() {
		return resourcesFolder;
	}

	@OutputDirectory
	public File getAtplugInfFolder() {
		return new File(resourcesFolder, PlugPlugin.ATPLUG_INF);
	}

	@InputFiles
	FileCollection classesFolders;

	public FileCollection getClassesFolders() {
		return classesFolders;
	}

	void setClassesFolders(Iterable<File> files) {
		// if we don't copy, Gradle finds an implicit dependency which
		// forces us to depend on `classes` even though we don't
		List<File> copy = new ArrayList<>();
		for (File file : files) {
			copy.add(file);
		}
		classesFolders = getProject().files(copy);
	}

	@TaskAction
	public void build() throws Throwable {
		// generate the metadata
		SortedMap<String, String> result = generate();

		// clean out the ATPLUG-INF folder, and put the map's content into the folder
		FileMisc.cleanDir(getAtplugInfFolder());
		for (Map.Entry<String, String> entry : result.entrySet()) {
			File serviceFile = new File(getAtplugInfFolder(), entry.getKey() + PlugPlugin.DOT_JSON);
			Files.write(serviceFile.toPath(), entry.getValue().getBytes(StandardCharsets.UTF_8));
		}

		// the resources directory *needs* the Service-Component entry of the manifest to exist in order for tests to work
		// so we'll get a manifest (empty if necessary, but preferably we'll load what already exists)
		Manifest manifest = loadManifest();
		String componentsCmd = atplugComponents();
		String componentsActual = manifest.getMainAttributes().getValue(PlugPlugin.SERVICE_COMPONENT);
		if (Objects.equals(componentsActual, componentsCmd)) {
			return;
		}
		// make sure there is a MANIFEST_VERSION, because otherwise the manifest won't write *anything*
		if (manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) == null) {
			manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		}
		// set the Service-Component entry
		if (componentsCmd == null) {
			manifest.getMainAttributes().remove(new Attributes.Name(PlugPlugin.SERVICE_COMPONENT));
		} else {
			manifest.getMainAttributes().putValue(PlugPlugin.SERVICE_COMPONENT, componentsCmd);
		}
		// and write out the manifest
		saveManifest(manifest);
	}

	private File manifestFile() {
		return new File(resourcesFolder, "META-INF/MANIFEST.MF");
	}

	private Manifest loadManifest() {
		Manifest manifest = new Manifest();
		if (manifestFile().isFile()) {
			try (InputStream input = new BufferedInputStream(Files.newInputStream(manifestFile().toPath()))) {
				manifest.read(input);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return manifest;
	}

	private void saveManifest(Manifest manifest) {
		FileMisc.mkdirs(manifestFile().getParentFile());
		try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(manifestFile().toPath()))) {
			manifest.write(output);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private SortedMap<String, String> generate() {
		PlugGeneratorJavaExecable input = new PlugGeneratorJavaExecable(new ArrayList<>(getClassesFolders().getFiles()), getJarsToLinkAgainst().getFiles());
		if (getLauncher().isPresent()) {
			WorkQueue workQueue = getWorkerExecutor().processIsolation(workerSpec -> {
				workerSpec.getClasspath().from(fromLocalClassloader());
				workerSpec.forkOptions(options -> {
					options.setExecutable(getLauncher().get().getExecutablePath());
				});
			});
			return JavaExecable.exec(workQueue, input).getAtplugInf();
		} else {
			input.run();
			return input.getAtplugInf();
		}
	}

	private String atplugComponents() {
		return atplugComponents(getAtplugInfFolder());
	}

	static String atplugComponents(File atplugInf) {
		if (!atplugInf.isDirectory()) {
			return null;
		} else {
			List<String> serviceComponents = new ArrayList<>();
			for (File file : FileMisc.list(atplugInf)) {
				if (file.getName().endsWith(PlugPlugin.DOT_JSON)) {
					serviceComponents.add(PlugPlugin.ATPLUG_INF + file.getName());
				}
			}
			Collections.sort(serviceComponents);
			return serviceComponents.stream().collect(Collectors.joining(","));
		}
	}

	static Set<File> fromLocalClassloader() {
		Set<File> files = new LinkedHashSet<>();
		Consumer<Class<?>> addPeerClasses = clazz -> {
			try {
				for (URL url : JRE.getClasspath(clazz.getClassLoader())) {
					String name = url.getFile();
					if (name != null) {
						files.add(new File(name));
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		// add the classes that we need
		addPeerClasses.accept(PlugGeneratorJavaExecable.class);
		// add the gradle API
		addPeerClasses.accept(JavaExec.class);
		// Needed because of Gradle API classloader hierarchy changes with 2c5adc8 in Gradle 6.7+
		addPeerClasses.accept(FileCollection.class);
		return files;
	}
}

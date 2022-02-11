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
package com.diffplug.atplug.tooling;


import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Preconditions;
import com.diffplug.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlugGenerator {

	/**
	 * Returns a Map from a plugin's name to its OSGI-INF content.
	 *
	 * @param toSearch			a directory containing class files where we will look for plugin implementations 
	 * @param toLinkAgainst		the classes that these plugins implementations need
	 * @return a map from component name to is OSGI-INF string content
	 */
	public static SortedMap<String, String> generate(List<File> toSearch, Set<File> toLinkAgainst) {
		try {
			PlugGeneratorJavaExecable ext = new PlugGeneratorJavaExecable(toSearch, toLinkAgainst);
			PlugGenerator metadataGen = new PlugGenerator(ext.toSearch, ext.toLinkAgainst);
			// save our results, with no reference to the guts of what happened inside PluginMetadataGen
			SortedMap<String, String> result = metadataGen.osgiInf;
			return result;
		} catch (Exception e) {
			if (Throwables.getRootCause(e) instanceof UnsatisfiedLinkError) {
				throw new RuntimeException("This is probably caused by a classpath sticking around from a previous invocation.  Run `gradlew --stop` and try again.", e);
			} else {
				throw Errors.asRuntime(e);
			}
		}
	}

	private final URLClassLoader classLoader;
	final SortedMap<String, String> osgiInf = new TreeMap<>();

	public static final String EXT_CLASS = ".class";

	PlugGenerator(List<File> toSearches, Set<File> toLinkAgainst) throws IOException, ClassNotFoundException, NoSuchFieldException, SecurityException {
		// create a classloader which looks in toSearch first, then each of the jars in toLinkAgainst
		URL[] urls = Stream.concat(toSearches.stream(), toLinkAgainst.stream())
				.map(Errors.rethrow().wrapFunction(file -> file.toURI().toURL()))
				.collect(Collectors.toList())
				.toArray(new URL[0]);

		ClassLoader parent = null; // explicitly set parent to null so that the classloader is completely isolated
		classLoader = new URLClassLoader(urls, parent);
		try {
			PlugParser parser = new PlugParser();
			// walk toSearch, passing each classfile to load()
			for (File toSearch : toSearches) {
				if (toSearch.isDirectory()) {
					Files.walkFileTree(toSearch.toPath(), new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
							if (file.toString().endsWith(EXT_CLASS)) {
								Errors.rethrow().run(() -> maybeGeneratePlugin(parser, file));
							}
							return FileVisitResult.CONTINUE;
						}
					});
				}
			}
		} finally {
			classLoader.close();
		}
	}

	/** Loads a class by its FQN.  If it's concrete and implements a plugin, then we'll call generatePlugin. */
	private void maybeGeneratePlugin(PlugParser parser, Path path) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		parser.parse(path.toFile());
		if (!parser.hasPlug()) {
			return;
		}

		Class<?> plugClass = classLoader.loadClass(parser.getPlugClassName());
		Class<?> socketClass = classLoader.loadClass(parser.getSocketClassName());
		if (Modifier.isAbstract(plugClass.getModifiers())) {
			throw new IllegalArgumentException("Class " + plugClass + " has @Plug(" + socketClass + ") but it is abstract.");
		}

		String osgiInfContent = generatePlugin(plugClass, socketClass);
		osgiInf.put(plugClass.getName(), osgiInfContent);
	}

	/** A cache from a plugin interface to a function that converts a class into its metadata. */
	private Map<Class<?>, Function<Class<?>, String>> metadataCreatorCache = new HashMap<>();

	@SuppressWarnings("unchecked")
	private <SocketT, PlugT extends SocketT> String generatePlugin(Class<?> plugClass, Class<?> socketClass) throws IllegalArgumentException, IllegalAccessException {
		Preconditions.checkArgument(socketClass.isAssignableFrom(plugClass), "Socket " + socketClass + " is not a supertype of @Plug " + plugClass);
		return generatePluginTyped((Class<PlugT>) plugClass, (Class<SocketT>) socketClass);
	}

	/**
	 * @param plugClass				The class for which we are generating plugin metadata.
	 * @param socketClass	The interface which is the socket for the metadata.
	 * @return					A string containing the content of OSGI-INF as appropriate for clazz.
	 */
	private <SocketT, PlugT extends SocketT> String generatePluginTyped(Class<PlugT> plugClass, Class<SocketT> socketClass) {
		Function<Class<?>, String> metadataCreator = metadataCreatorCache.computeIfAbsent(socketClass, interfase -> {
			try {
				// DeclarativeMetadataCreator<Socket>
				Class<?> socketClazz = classLoader.loadClass(interfase.getName());
				Field socketField = socketClazz.getDeclaredField("socket");
				Object socket = socketField.get(null);
				Class<?> socketOwnerClazz = socket.getClass();
				Method metadata = socketOwnerClazz.getMethod("asDescriptor", Object.class);
				metadata.setAccessible(true);
				return (Class<?> instanceClass) -> {
					try {
						return (String) metadata.invoke(socket, instantiate(instanceClass));
					} catch (Exception e) {
						Throwable rootCause = Throwables.getRootCause(e);
						if (rootCause instanceof java.lang.ClassNotFoundException) {
							throw new RuntimeException("Unable to generate metadata for " + instanceClass +
									", missing transitive dependency " + rootCause.getMessage(), e);
						} else {
							throw new RuntimeException("Unable to generate metadata for " + instanceClass +
									", make sure that its metadata methods return simple constants: " + e.getMessage(), e);
						}
					}
				};
			} catch (Exception e) {
				throw new RuntimeException("To create plugin metadata around " + plugClass + " for socket " + socketClass +
						", we look for a static final field of SocketOwner which must be named `socket`.", e);
			}
		});
		return metadataCreator.apply(plugClass);
	}

	/** Calls the no-arg constructor of the given class, even if it is private. */
	static <T> T instantiate(Class<? extends T> clazz) throws Exception {
		Constructor<?> constructor = null;
		for (Constructor<?> candidate : clazz.getDeclaredConstructors()) {
			if (candidate.getParameterCount() == 0) {
				constructor = candidate;
				break;
			}
		}
		Objects.requireNonNull(constructor, "Class must have a no-arg constructor, but it didn't.  " + clazz + " " + Arrays.asList(clazz.getDeclaredConstructors()));
		@SuppressWarnings("unchecked")
		T instance = (T) constructor.newInstance();
		return instance;
	}
}

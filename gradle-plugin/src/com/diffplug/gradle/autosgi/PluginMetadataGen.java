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
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import com.diffplug.common.base.Box;
import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Preconditions;
import com.diffplug.common.base.Throwables;
import com.diffplug.common.collect.ImmutableList;
import com.diffplug.gradle.JavaExecable;

public class PluginMetadataGen {
	/**
	 * {@link PluginMetadataGen#generate(File, Set, Set)} in a {@link JavaExecable} form.
	 */
	public static class External implements JavaExecable {
		private static final long serialVersionUID = -3051458941508147719L;

		// inputs
		File toSearch;
		Set<File> toLinkAgainst;

		public External(File toSearch, Set<File> toLinkAgainst) {
			this.toSearch = toSearch;
			this.toLinkAgainst = toLinkAgainst;
		}

		// outputs
		Map<String, String> osgiInf;

		@Override
		public void run() throws Throwable {
			PluginMetadataGen metadataGen = new PluginMetadataGen(toSearch, toLinkAgainst);
			osgiInf = metadataGen.osgiInf;
		}
	}

	/**
	 * Returns a Map from a plugin's name to its OSGI-INF content.
	 *
	 * @param toSearch			a directory containing class files where we will look for plugin implementations 
	 * @param toLinkAgainst		the classes that these plugins implementations need
	 * @param pluginInterfaces	the plugin interfaces that we're look for implementations of
	 * @return a map from component name to is OSGI-INF string content
	 */
	public static Map<String, String> generate(File toSearch, Set<File> toLinkAgainst) {
		GcTracker tracker = new GcTracker();
		try {
			PluginMetadataGen metadataGen = new PluginMetadataGen(toSearch, toLinkAgainst);
			tracker.track(metadataGen.classLoader);
			// save our results, with no reference to the guts of what happened inside PluginMetadataGen
			Map<String, String> result = metadataGen.osgiInf;
			if (!metadataGen.loadedJni.isEmpty()) {
				// if we loaded JNI, that means that future loads will fail if we don't GC the classloader we created inside PluginMetadataGen
				// null out all reference to whatever was in PluginMetadataGen, and try to force a GC
				metadataGen = null;
				tracker.tryGcUntilEmpty(10, 250, TimeUnit.MILLISECONDS);
			}
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
	private final Map<String, String> osgiInf = new HashMap<>();
	private final ImmutableList<String> loadedJni;

	private static final String EXT_CLASS = ".class";

	private PluginMetadataGen(File toSearch, Set<File> toLinkAgainst) throws IOException, ClassNotFoundException, NoSuchFieldException, SecurityException {
		// create a classloader which looks in toSearch first, then each of the jars in toLinkAgainst
		URL[] urls = Stream.concat(Stream.of(toSearch), toLinkAgainst.stream())
				.map(Errors.rethrow().wrapFunction(file -> file.toURI().toURL()))
				.collect(Collectors.toList())
				.toArray(new URL[0]);

		ClassLoader parent = null; // explicitly set parent to null so that the classloader is completely isolated
		classLoader = new URLClassLoader(urls, parent);
		try {
			// walk toSearch, passing each classfile to load()
			Files.walkFileTree(toSearch.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.toString().endsWith(EXT_CLASS)) {
						Errors.rethrow().run(() -> maybeGeneratePlugin(file));
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} finally {
			loadedJni = JniAccess.getLoadedLibraries(classLoader);
			classLoader.close();
		}
	}

	private static final String PLUG = "Lcom/diffplug/plugin/Plug;";

	static String asmToJava(String className) {
		return className.replace("/", ".");
	}

	/** Loads a class by its FQN.  If it's concrete and implements a plugin, then we'll call generatePlugin. */
	private void maybeGeneratePlugin(Path path) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		ClassReader reader = new ClassReader(Files.readAllBytes(path));
		String plugClassName = asmToJava(reader.getClassName());
		Box.Nullable<String> socketClassName = Box.Nullable.ofNull();
		reader.accept(new ClassVisitor(Opcodes.ASM5) {
			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if (PLUG.equals(desc)) {
					return new AnnotationVisitor(Opcodes.ASM5) {
						@Override
						public void visit(String name, Object value) {
							Preconditions.checkArgument(name.equals("value"), "For @Plug %s, expected 'value' but was '%s'", plugClassName, name);
							Preconditions.checkArgument(socketClassName.get() == null, "For @Plug %s, multiple annotations: '%s' and '%s'", plugClassName, socketClassName.get(), value);
							socketClassName.set(((org.objectweb.asm.Type) value).getClassName());
						}
					};
				} else {
					return null;
				}
			}
		}, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE);
		if (socketClassName.get() == null) {
			return;
		}

		Class<?> plugClass = classLoader.loadClass(plugClassName);
		Class<?> socketClass = classLoader.loadClass(socketClassName.get());
		if (Modifier.isAbstract(plugClass.getModifiers())) {
			throw new IllegalArgumentException("Class " + plugClass + " has @Plug(" + socketClass + ") but it is abstract.");
		}

		String osgiInfContent = generatePlugin(plugClass, socketClass);
		osgiInf.put(plugClass.getName(), osgiInfContent);
	}

	/** A cache from a plugin interface to a function that converts a class into its metadata. */
	private Map<Class<?>, Function<Class<?>, String>> metadataCreatorCache = new HashMap<>();
	/** If a plugin class is named com.package.Socket, then it must have a DeclarativeMatadataCreator<Socket> at com.package.Socket$MetadataCreator. */
	private static final String METADATA_CREATOR = "$MetadataCreator";
	private static final String METADATA_CREATOR_METHOD = "configFor";

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
				Class<?> creatorClazz = classLoader.loadClass(socketClass.getName() + METADATA_CREATOR);
				Object instance = creatorClazz.newInstance();
				// String DeclarativeMetadataCreator<Socket>::configFor(Class<? extends Socket> clazz)
				Method method = creatorClazz.getMethod(METADATA_CREATOR_METHOD, Class.class);
				return (Class<?> instanceClass) -> {
					try {
						return (String) method.invoke(instance, instanceClass);
					} catch (Exception e) {
						throw new RuntimeException("Unable to generate metadata for " + instanceClass + ", " +
								" make sure that its metadata methods return simple constants.", e);
					}
				};
			} catch (Exception e) {
				throw new RuntimeException("To create plugin metadata around " + plugClass + " for socket " + socketClass +
						", we look for an instance of DeclarativeMetadataCreator<" + socketClass + "> which must be named " +
						socketClass + METADATA_CREATOR, e);
			}
		});
		return metadataCreator.apply(plugClass);
	}

	/** Provids access to JNI libraries, compliments of http://stackoverflow.com/a/1008631/1153071. */
	public static class JniAccess {
		private static final java.lang.reflect.Field LIBRARIES;

		static {
			try {
				LIBRARIES = ClassLoader.class.getDeclaredField("loadedLibraryNames");
				LIBRARIES.setAccessible(true);
			} catch (NoSuchFieldException | SecurityException e) {
				throw Errors.asRuntime(e);
			}
		}

		public static ImmutableList<String> getLoadedLibraries(ClassLoader loader) {
			return Errors.rethrow().get(() -> {
				@SuppressWarnings("unchecked")
				Vector<String> libraries = (Vector<String>) LIBRARIES.get(loader);
				return ImmutableList.copyOf(libraries);
			});
		}
	}
}

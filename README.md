# <img align="left" src="_images/logo_128.png"> AutOSGi: Sockets and Plugs without boilerplate

<!---freshmark shields
output = [
	link(shield('Maven artifact', 'mavenCentral', '{{group}}:{{name}}', 'blue'), 'https://bintray.com/{{org}}/opensource/{{name}}/view'),
	link(shield('Gradle plugin', 'plugins.gradle.org', 'com.diffplug.gradle.autosgi', 'blue'), 'https://plugins.gradle.org/plugin/com.diffplug.gradle.autosgi'),
	'',
	link(shield('Latest version', 'latest', '{{stable}}', 'brightgreen'), 'https://github.com/{{org}}/{{name}}/releases/latest'),
	link(shield('Javadoc', 'javadoc', 'OK', 'brightgreen'), 'https://{{org}}.github.io/{{name}}/javadoc/{{stable}}/'),
	link(shield('License Apache', 'license', 'Apache', 'brightgreen'), 'https://tldrlegal.com/license/apache-license-2.0-(apache-2.0)'),
	link(shield('Changelog', 'changelog', '{{version}}', 'brightgreen'), 'CHANGES.md'),
	// link(image('Travis CI', 'https://travis-ci.org/{{org}}/{{name}}.svg?branch=master'), 'https://travis-ci.org/{{org}}/{{name}}'),
	link(shield('Live chat', 'gitter', 'live chat', 'brightgreen'), 'https://gitter.im/diffplug/autosgi')
	].join('\n');
-->
[![Maven artifact](https://img.shields.io/badge/mavenCentral-com.diffplug.autosgi%3Aautosgi-blue.svg)](https://bintray.com/diffplug/opensource/autosgi/view)
[![Gradle plugin](https://img.shields.io/badge/plugins.gradle.org-com.diffplug.gradle.autosgi-blue.svg)](https://plugins.gradle.org/plugin/com.diffplug.gradle.autosgi)

[![Latest version](https://img.shields.io/badge/latest-unreleased-brightgreen.svg)](https://github.com/diffplug/autosgi/releases/latest)
[![Javadoc](https://img.shields.io/badge/javadoc-OK-brightgreen.svg)](https://diffplug.github.io/autosgi/javadoc/unreleased/)
[![License Apache](https://img.shields.io/badge/license-Apache-brightgreen.svg)](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))
[![Changelog](https://img.shields.io/badge/changelog-unreleased-brightgreen.svg)](CHANGES.md)
[![Live chat](https://img.shields.io/badge/gitter-live_chat-brightgreen.svg)](https://gitter.im/diffplug/autosgi)
<!---freshmark /shields -->

<!---freshmark javadoc
output = prefixDelimiterReplace(input, 'https://{{org}}.github.io/{{name}}/javadoc/', '/', stable);
-->

## AutOSGi is...

- a plugin system for the JVM
- that generates all OSGi metadata for you - write Java code, not error-prone metadata
- that runs with or without OSGi
	+ No need for OSGi in small systems (e.g. unit tests)
	+ Take full advantage of OSGi's power in large systems

AutOSGi has two components:

- a small runtime (less than 1000 lines) which allows seamless operation inside and outside of OSGi
- a buildtime step which generates OSGi declarative service metadata
	+ Gradle plugin: [`com.diffplug.gradle.autosgi`](https://plugins.gradle.org/plugin/com.diffplug.gradle.autosgi)
	+ Contributions welcome for maven, ant, etc.

It is currently in production usage at [DiffPlug](https://www.diffplug.com); extracting it into an opensource project is a WIP.  Will reach 1.0 by Devoxx US (March 2017).

## How it works

Let's say you're building a filesystem explorer, and you'd like to present a plugin interface for adding items to the right click menu.  The socket interface might look something like this:

```java
public interface FileMenu {
	/** Adds the appropriate entries for the given list of files. */
	void addRightClick(Menu root, List<File> files);
}
```

Let's say our system has 100 different `FileMenu` plugins.  Loading all 100 plugins will take a long time, so we'd like to describe which files a given `FileMenu` applies to without having to actually load it.  One way would be if each `FileMenu` declared which file extensions it is applicable to.

We can accomplish this in AutOSGi by adding a method to the socket interface marked with `@Metadata`.  The annotation is a documentation hint that this method should return a constant value which will be used to generate static metadata about the plugin.

```java
public interface FileMenu {
	/** Extensions for which this FileMenu applies (empty set means it applies to all extensions). */
	@Metadata default Set<String> extensions() {
		return Collections.emptySet();
	}

	/** Adds the appropriate entries for the given list of files. */
	void addRightClick(Menu root, List<File> files);
}
```

The OSGi runtime (and AutOSGi's non-OSGi compatibility layer) can store metadata about a plugin in a `Map<String, String>` which gets saved into a metadata file.  This is the mechanism which allows us to inspect all the `FileMenu` plugins in the system without loading their classes.

To take advantage of this, we need to declare a class `FileMenu.MetadataCreator extends `[`DeclarativeMetadataCreator<FileMenu>`](TODO-javadoc), which will take a `FileMenu` instance and return a `Map<String, String>` (a.k.a. `Function<FileMenu, Map<String, String>>`).  This will be used during the build step to generate OSGi metadata files.

In order to read this metadata at runtime, we also need to declare a class `FileMenu.Descriptor extends `[`ServiceDescriptor<FileMenu>`](TODO-javadoc) which will parse the `Map<String, String>` into a convenient form for determining which plugins to load.

In the case of our `FileMenu` socket, implementing `MetadataCreator` and `Descriptor` mostly boils down to turning a `Set<String>` of extensions into a `Map<String, String>`.  There are lots of ways to do this, but the clearest is probably to turn the set `[doc, docx]` into the map `extensions=doc,docx`, where we encode the set using a single comma-delimited string.  This way if we decide later to add other metadata like `int minFiles()` or `int maxFiles()`, then we can trivially update the metadata map to `extensions=doc,docx minFiles=1 maxFiles=1`.  The project `[durian-parse](TODO-link)` has a variety of useful converters for going back and forth between simple data structures and raw strings.

Here's how we might implement our FileMenu.MetadataCreator and FileMenu.Descriptor.

```java
public interface FileMenu {
	...
	/** Generates metadata from an instance of FileMenu (implementation detail). */
	static class MetadataCreator extends DeclarativeMetadataCreator<FileMenu> {
		private static final String KEY_EXTENSIONS = "extensions";

		public MetadataCreator() {
			super(FileMenu.class, instance -> ImmutableMap.of(KEY_EXTENSIONS, Converters.forSet().convert(instance.fsPrefixes()));
		}
	}

	/**
	* Parses a descriptor of a FileMenu from its metadata.
	* Public API for exploring the registry of available plugins.
	*/
	public static final class Descriptor extends ServiceDescriptor<FileMenu> {
		final Set<String> extensions;

		private Descriptor(ServiceReference<FileMenu> ref) {
			super(ref);
			this.extensions = Converters.forSet().reverse().convert(getString(MetadataCreator.KEY_EXTENSIONS));
		}

		private boolean appliesTo(List<Filder> files) {
			return extensions.stream().allMatch(extension -> {
				return files.stream().allMatch(file -> file.getName().endsWith(extension));
			});
		}

		/** Returns descriptors for all RightClickFiles which apply to the given list of files. */
		public static Stream<Descriptor> getFor(List<Filder> files) {
			return ServiceDescriptor.getServices(FileMenu.class, Descriptor::new).filter(d -> d.appliesTo(files));
		}
	}
}
```

Now, when we want to implement a right-click menu, all we have to do is mark it with the `@Plug` annotation so that the build step can find it.

```java
@Plug
public class DocxFileMenu implements FileMenu {
	/** Extensions for which this FileMenu applies (empty set means it applies to all extensions). */
	@Override public Set<String> extensions() {
		return ImmutableSet.of("doc", "docx");
	}

	/** Adds the appropriate entries for the given list of files. */
	@Override public void addRightClick(Menu root, List<File> files) {
		// do stuff
	}
}
```

When we run `gradlew generateOsgiMetadata` (which will run automatically whenever it is needed), AutOSGi's build step will generate these files for us:

```
--- OSGI-INF/com.diffplug.talks.socketsandplugs.DocxFileMenu.xml ---
<component name="com.diffplug.talks.socketsandplugs.DocxFileMenu">
	<implementation class="com.diffplug.talks.socketsandplugs.DocxFileMenu"></implementation>
	<service>
		<provide interface="com.diffplug.talks.socketsandplugs.FileMenu"></provide>
	</service>
	<property name="extensions" type="String" value="doc,docx"></property>
</component>

--- META-INF/MANIFEST.MF ---
Service-Component: OSGI-INF/com.diffplug.talks.socketsandplugs.DocxFileMenu.xml
```

AutOSGi ensures that you'll never have to edit these files by hand, but there's no magic.  You write the function that generates the metadata (MetadataCreator) and you write the function that parses the metadata (Descriptor).  AutOSGi just does all the plumbing and grunt work for you.

To use the plugin system, all you have to do is:

```java
Menu root = new Menu();
List<File> files = Arrays.asList(new File("Budget.docx"));
for (FileMenu.Descriptor descriptor : FileMenu.Descriptor.getFor(files)) {
	descriptor.openManaged(instance -> {
		instance.addRightClick(root, files);
	});
}
```

<!---freshmark /javadoc -->

## Requirements

Nothing so far...

## Acknowledgements

* Formatted by [spotless](https://github.com/diffplug/spotless), [as such](https://github.com/diffplug/durian-rx/blob/v1.0/build.gradle?ts=4#L70-L90).
* Bugs found by [findbugs](http://findbugs.sourceforge.net/), [as such](https://github.com/diffplug/durian-rx/blob/v1.0/build.gradle?ts=4#L92-L116).
* Built by [gradle](http://gradle.org/).
* Tested by [junit](http://junit.org/).
* Maintained by [DiffPlug](http://www.diffplug.com/).

# <img align="left" src="_images/logo_128.png"> AtPlug: Sockets and Plugs without boilerplate

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.diffplug.atplug?color=blue&label=gradle%20plugin%20portal)](https://plugins.gradle.org/plugin/com.diffplug.atplug)
[![Maven central](https://img.shields.io/maven-central/v/com.diffplug.atplug/atplug-runtime?color=blue&label=com.diffplug.atplug%3Aatplug-runtime)](https://search.maven.org/artifact/com.diffplug.atplug/atplug-runtime)
[![Changelog](https://img.shields.io/badge/changelog-here-blue)](CHANGELOG.md)
[![Apache 2.0](https://img.shields.io/badge/license-apache--2.0-blue.svg)](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

## AtPlug is...

- a plugin system for the JVM
  - written in pure Kotlin, might port to Kotlin Multiplatform [someday](https://github.com/diffplug/atplug/issues/1).
- that generates all plugin metadata for you
  - write Java/Kotlin/Scala code, *never* write error-prone metadata manually
- lets you filter the available plugins based on their metadata
  - defer classloading to the last possible instant
- easy mocking for unit tests

AtPlug has three components:

- a small runtime `com.diffplug.atplug:atplug-runtime`
- a buildtime step which generates plugin metadata
  - Gradle plugin: [`com.diffplug.atplug`](https://plugins.gradle.org/plugin/com.diffplug.atplug)
  - Contributions welcome for maven, etc.
- a harness for mocking in tests `com.diffplug.atplug:atplug-test-harness`
  - built-in support for JUnit5, PRs for other test frameworks welcome

It is in production usage at [DiffPlug](https://www.diffplug.com).

## How it works

Let's say you're building a drawing application, and you want a plugin system to allow users to contribute different shapes. The socket interface might look something like this:

```kotlin
interface Shape {
  fun draw(g: Graphics)
}
```

Let's say our system has 100 different `Shape` plugins.  Loading all 100 plugins will take a long time, so we'd like to describe which shapes are available without having to actually load it.

We can accomplish this in AtPlug by adding a method to the socket interface marked with `@Metadata`.  The annotation is a documentation hint that this method should return a constant value which will be used to generate static metadata about the plugin.

```kotlin
interface Shape {
  @Metadata fun name(): String
  @Metadata fun previewSvgIcon(): String
  fun draw(g: Graphics)
}
```

The AtPlug runtime stores metadata about a plugin in a `Map<String, String>` which gets saved into a metadata file.  This is the mechanism which allows us to inspect all the `Shape` plugins in the system without loading their classes.

To take advantage of this, we need to an object `Shape.Socket : SocketOwner` which will take a `Shape` instance and return a `Map<String, String>`.  This will be used during the build step to generate AtPlug metadata files.

```kotlin
interface Shape {
  object Socket : SocketOwner.SingletonById<Shape>(Shape::class.java) {
    const val KEY_SVG_ICON = "svgIcon"
    override fun metadata(plug: Shape) = mapOf(
            Pair(KEY_ID, plug.name()),
            Pair(KEY_SVG_ICON, plug.previewSvgIcon()))
  }
}
```

Now your users can declare an instance of `Shape` and annotate it with `@Plug(Shape.class)`.

```kotlin
@Plug(Shape::class)
class Circle : Shape {
  override fun name() = "Circle"
  override fun previewSvgIcon() = "icons/circle.svg"
  override fun draw(g: Graphics) = g.drawCircle()
}
```

Now when you run `./gradlew jar`, you will have a resource file called `ATPLUG-INF/com.package.Circle.json` with content like this:

```json
{ "implementation": "com.package.Circle",
  "provides": "com.api.Shape",
  "properties": {
    "id": "Circle",
    "svgIcon": "icons/circle.svg"
  }
}
```

And the manifest of the Jar file will have a field `AtPlug-Component` which points to all the json files in the `ATPLUG-INF` directory. You never have to edit these files, but there's no magic. The `metadata` function which you wrote for the socket generates all the json files.

To use the plugin system, you can do:

```kotlin
Shape.Socket.availableIds(): List<String>
Shape.Socket.descriptorForId(id: String): PlugDescriptor?
Shape.Socket.singletonForId(id: String): Shape?
```

Which are all public methods of `SocketOwner.SingletonById`. You can add more methods too for your usecase.

### (Id vs Descriptor) and (Singleton vs Ephemeral)

The `Socket` is responsible for:

- generating metadata (at buildtime)
- maintaining the runtime registry of available plugins
- instantiating the actual objects from their metadata

When it comes to the registry of available plugins, there are two obvious design points:

- declare some String which functions as a unique id => `Id`
- parse the `Map<String, String>` into a descriptor class, and run filters against the set of parsed descriptors to get all the plugins which apply to a given situation => `Descriptor`.

When it comes to instantiating the actual objects from their metadata, there are again two obvious designs:

- Once a plugin is instantiated, cache it forever and return the same instance each time => `Singleton`
- Call the plugin constructor each time it is instantiated, so that you may end up with multiple instances of a single plugin, and unused instances can be garbage collected => `Ephemeral`

In most cases, if a plugin has a unique id, then it also makes sense to treat that plugin as a global singleton => `SocketOwner.SingletonById`. Likewise, if plugins do not have unique ids, then their concept of identity probably doesn't matter so there's no need to cache them as singletons => `SocketOwner.EphemeralByDescriptor`.

Those two classes, `SingletonById` and `EphemeralByDescriptor`, are the only two options we provide out of the box - we did not fill the full 2x2 matrix (no `SingletonByDescriptor` or `EphemeralById`) because we have not found a need anywhere in our codebase for the other cases. You are free to implement `SocketOwner` yourself from scratch if you want a different design point.

The public methods of `SingletonById` are just above this section. `EphemeralByDescriptor` doesn't have any public methods, only protected methods which you can use to build an API appropriate to your case.

```kotlin
abstract class EphemeralByDescriptor<T, ParsedDescriptor> {
  protected abstract fun parse(plugDescriptor: PlugDescriptor): ParsedDescriptor
  protected fun <R> computeAgainstDescriptors(compute: Function<Set<ParsedDescriptor>, R>) : R
  protected fun <R> forEachDescriptor(forEach: Consumer<ParsedDescriptor>)
  protected fun descriptorsFor(predicate: Predicate<ParsedDescriptor>): List<ParsedDescriptor>
  protected fun instantiateFor(predicate: Predicate<ParsedDescriptor>): List<T>
  protected fun instantiateFirst(predicateDescriptor: Predicate<ParsedDescriptor>, order: Comparator<ParsedDescriptor>, predicateInstance: Predicate<T>): T?
}
```

### Working from Java

The examples above are Kotlin, but you can also use Java. To declare the socket, just have a field `static final SocketOwner socket`, as shown below:

```java
public interface Shape {
  public static final SocketOwner.Id<Shape> socket = new SocketOwner.SingletonId<Shape>(Shape.class) {
    @Override
    public Map<String, String> metadata(Shape plug) {
      Map<String, String> map = new HashMap<>();
      map.put(KEY_ID, plug.name());
      return map;
    }
  };
}
```

Sockets don't have to be interfaces - abstract classes or even concrete classes would work fine too.

### OSGi compatibility

This project used to be called "AutOSGi", and rather than generating `.json` it generated metadata compatible with OSGi Declarative Services. We found that OSGi caused more trouble than it was worth, and ended up removing it. However, it would be pretty easy to add it back in, see the [`graveyard/osgi` tag](https://github.com/diffplug/atplug/releases/tag/graveyard%2Fosgi) to get back to the OSGi version. Happy to merge a PR which optionally puts this functionality back in.

## Requirements

Java 8+.

## Acknowledgements

* Maintained by [DiffPlug](http://www.diffplug.com/).

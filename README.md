# MeshDI

MeshDI is a Java dependency injection framework. It provides a lightweight API for registering bindings, resolving beans, managing bean lifecycle, and composing multiple contexts into a mesh.

## Modules

- `meshdi-api` - public API, annotations, scopes, lifecycle contracts, and exceptions.
- `meshdi-core` - default container implementation, binding container, bean resolver, scopes, and context mesh logic.
- `meshdi-processor` - annotation processor support for MeshDI metadata.
- `meshdi-moduleit` - module layer for meshdi.
- `meshdi-example` - example usage of meshdi framework with modules.

## Features

- Constructor and factory bindings
- Singleton and prototype scopes
- Lazy and eager initialization strategies
- `@Inject`, `@Dependency`, `@Component`, and `@OnLifecycleEvent` annotations
- Context graph / mesh support
- Compile-time dependency analysis
- Dynamic Module loading / unloading
- Semantic Version resolution

## Requirements

- JDK 25 or newer
- Gradle (the wrapper scripts are in the repository, the wrapper jar is not committed)

## Build

```bash
./gradlew build
```

## Test

```bash
./gradlew test
```

## Basic Usage

```java
import me.bottdev.kern.commons.key.SimpleTypedKey;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;

var serviceKey = SimpleTypedKey.of(MyService.class);

var context = new SimpleContextBuilder()
        .id("app")
        .construct(serviceKey, binding -> binding
                .implementation(MyService.class)
                .singleton()
                .lazy())
        .build();

context.start();

MyService service = context.getResolver().get(serviceKey);
```

## Resolution rules

- `get` throws `BeanResolvationException` when nothing is bound to the key. Use `find`, which returns an `Optional`, for beans that may be absent.
- A dependency marked `@Dependency(link = DependencyLink.OPTIONAL)` is injected as `null` when it is absent.
- Binding the same key twice in one context fails with `DuplicateBindingException`. Use `override(...)` on the builder to replace a binding on purpose.
- In a mesh, a key bound by more than one reachable context fails with `MeshAmbiguousLookupException` instead of picking one silently.
- A singleton is created once even under concurrent access and is published only after its `POST_CONSTRUCT` handlers have completed.
- `@Inject` marks a constructor and `@Dependency` marks a constructor parameter. Field injection is not supported.

## Modules

- The `@Module` annotation processor writes the module descriptor into the jar as a data file (`ModuleDescriptorCodec` format). Loading a candidate never executes code from the jar before the module is accepted. Modules built with an older processor must be rebuilt.
- Repositories declared by a module must use `https`. They are used only for that module's isolated libraries; shared libraries are always resolved against the repositories configured by the host.
- Packages exported by a module are visible only to modules that declare a dependency on it. Two modules cannot export the same package.
- A module whose start fails is rolled back (its context is removed from the mesh and disposed) and can be started again or unloaded.

## Example

```bash
./gradlew :meshdi-example:app:copyJar :meshdi-example:root-module:copyJar :meshdi-example:tool-module:copyJar
cd build/example-run && java -jar app-*-all.jar
```

Pass `-Pmeshdi.example.dir=/some/path` to assemble the example somewhere else.

## Project Status

MeshDI is currently in early development.

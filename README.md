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
- `@Inject`, `@Dependency`, `@Component`, and `@PreDestroy` annotations
- Context graph / mesh support
- Compile-time dependency analysis
- Dynamic Module loading / unloading
- Semantic Version resolution

## Requirements

- JDK 23 or newer
- Gradle Wrapper included in the repository

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

## Project Status

MeshDI is currently in early development.

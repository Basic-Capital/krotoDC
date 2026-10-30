# krotoDC

krotoDC is a protoc plugin for generating Kotlin data classes and gRPC service/stub from a .proto input. This library makes it easy to work with Protocol Buffers and gRPC in your Kotlin projects.

check out my [blog post](https://medium.com/@icycle0409/introducing-krotodc-use-protobuf-and-grpc-with-kotlin-dataclasses-3144d0b20032) that introduces krotoDC
## Features

- Generates Kotlin data classes from .proto files
- Generates Converter extension methods between generated classes and GeneratedMessageV3 classes
- Generates gRPC service/stub based on the service definition in .proto files
- Supports Kotlin specific features like nullable fields and sealed oneof classes: 

  see the generated code spec [here](https://github.com/mscheong01/krotoDC/blob/main/generator/README.md)

## Installation

In your project's `build.gradle.kts` file, add the following dependencies:

```kotlin
dependencies {
    implementation("com.google.protobuf:protobuf-java:4.30.1")
    implementation("com.google.protobuf:protobuf-java-util:4.30.1")
    implementation("io.grpc:grpc-stub:1.70.0")
    implementation("io.grpc:grpc-kotlin-stub:1.4.0")
    implementation("io.github.mscheong01:krotoDC-core:1.2.0")
}
```

## Usage

1. Configure the protobuf plugin in your `build.gradle.kts` file:

```kotlin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.70.1"
        }
        id("krotoDC") {
            artifact = "io.github.mscheong01:protoc-gen-krotoDC:1.2.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("krotoDC")
            }
        }
    }
}
```

2. Create your `.proto` files with your message and service definitions.

3. Build your project, and the plugin will generate Kotlin data classes and gRPC service/stub based on the `.proto` files.

## Field accessors (`fieldAccessors=true`)

With `option("fieldAccessors=true")`, every data class gets a typed, reflection-free list of its proto fields. Off by
default, and without it the generated code is unchanged. It works with either converter location.

```kotlin
// message Ledger { string id = 1; optional int32 limit = 2; repeated Owner owners = 3; oneof contact { string email = 4; } }
val field: KrotoDCField<Ledger, String> = Ledger.Fields.id
field.get(ledger)            // String?
field.has(ledger)            // proto semantics, see below
field.set(ledger, "L-1")     // a copy; set(ledger, null) clears

for (f in Ledger.Fields.all) println("${f.number} ${f.jsonName} ${f.kind} ${f.cardinality}")
// With converterLocation=member, the companion returns the same list: Ledger.fields (KrotoDCMessageCompanion.fields)
```

- `Ledger.Fields` holds one `@JvmField val <propertyName>: KrotoDCField<Ledger, V>` per field, and `all`, a list of them
  in proto declaration order with oneof members in place (`all_` if a field is named `all`). Messages without fields
  get no object. If the message already has a nested type named `Fields` (a nested message, or a oneof named `fields`)
  or is itself named `Fields`, the object is `KrotoFields` instead (then `KrotoFields2`, ...).
- With `converterLocation=member`, `Ledger.fields` (the companion's `KrotoDCMessageCompanion.fields`) returns
  `Ledger.Fields.all`; it is empty for messages without fields and for code generated without the option. With the
  default top-level converters there is no companion interface, so `Fields.all` is the entry point.
- `KrotoDCField<D : Any, V : Any>`: `D` is the data class, which only implements `KrotoDCMessage` in member mode.
- `V` is the property type without nullability: Kotlin scalars, `ByteString`, the protoc Java enum, the generated data
  class, `List<T>`, `Map<K, V>`, and the mapped type for well-known types (`LocalDateTime`, `java.time.Duration`,
  `Int` for `Int32Value`, `Unit` for `Empty`, the protoc Java class for e.g. `Any`). For a oneof member, the case's value.
- `kind` is the protobuf Java type (element type for repeated fields), so map fields and every well-known type are
  `MESSAGE`.
- `messageFields`, for `MESSAGE` fields backed by a data class (elements for repeated), is a function returning that
  data class's `Fields.all` (empty if it has no fields), so code can recurse into nested messages in either mode; null
  for other kinds, maps and well-known types. `messageCompanion` is the generated companion for the same fields with
  `converterLocation=member`, and always null with top-level converters.
- `cardinality`: `OPTIONAL` for nullable properties (`optional`, wrapper types) and oneof members; `IMPLICIT` for other
  singular fields, including message fields, which krotoDC gives a non-null default; `REPEATED`; `MAP`.
- `has`: `OPTIONAL` when non-null (oneof member: its case is selected); `IMPLICIT` when different from the default
  (floating point compared bitwise like protobuf-java, so `-0.0` is set; a message equal to its default instance is not);
  `REPEATED`/`MAP` when non-empty.
- `set(m, null)`: `OPTIONAL` becomes null, `IMPLICIT` takes the constructor's default (a message converted from the proto
  default instance when `allFieldDefaults` is off), collections become empty; a oneof member clears the oneof only if
  its own case is selected.
- Accessors are built on first use of the `Fields` object (the companion's `fields` is a getter onto it), and
  `messageFields` is a lambda, so mutually recursive messages can't deadlock or observe a null during class
  initialization.
- Generate every file that references another file's messages with the same `fieldAccessors` setting: `messageFields`
  refers to the other message's `Fields` object.

## Snapshot Versions
krotoDC provides snapshot versions that are automatically released when changes are pushed to the main branch. 
The current snapshot version is the next minor version of the current release version with a -SNAPSHOT suffix. 
For example, if the current release is 1.2.3, the snapshot version will be 1.3.0-SNAPSHOT.

To use snapshot versions, add the maven snapshot repository to your build configuration
```kotlin
repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
```
## Contributing

Contributions are welcome! Please see our [contributing guidelines](https://github.com/mscheong01/krotoDC/blob/main/CONTRIBUTING.md) for more information.

## License

This project is licensed under the [Apache 2.0 License](https://github.com/mscheong01/krotoDC/blob/main/LICENSE).

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

With `option("converterLocation=member")` and `option("fieldAccessors=true")`, every data class gets a typed,
reflection-free list of its proto fields. Off by default; the option is rejected without `converterLocation=member`.

```kotlin
// message Ledger { string id = 1; optional int32 limit = 2; repeated Owner owners = 3; oneof contact { string email = 4; } }
val field: KrotoDCField<Ledger, String> = Ledger.Fields.id
field.get(ledger)            // String?
field.has(ledger)            // proto semantics, see below
field.set(ledger, "L-1")     // a copy; set(ledger, null) clears

for (f in Ledger.fields) println("${f.number} ${f.jsonName} ${f.kind} ${f.cardinality}")  // KrotoDCMessageCompanion.fields
```

- `Ledger.Fields` holds one `@JvmField val <propertyName>: KrotoDCField<Ledger, V>` per field. If the message already has
  a nested type named `Fields` (a nested message, or a oneof named `fields`) or is itself named `Fields`, the object is
  `KrotoFields` instead (then `KrotoFields2`, ...).
- `Ledger.fields` (the companion's `KrotoDCMessageCompanion.fields`) lists them in proto declaration order, oneof members
  in place. It is empty for messages without fields and for code generated without the option.
- `V` is the property type without nullability: Kotlin scalars, `ByteString`, the protoc Java enum, the generated data
  class, `List<T>`, `Map<K, V>`, and the mapped type for well-known types (`LocalDateTime`, `java.time.Duration`,
  `Int` for `Int32Value`, `Unit` for `Empty`, the protoc Java class for e.g. `Any`). For a oneof member, the case's value.
- `kind` is the protobuf Java type (element type for repeated fields), so map fields and every well-known type are
  `MESSAGE`. `messageCompanion` is the generated companion for `MESSAGE` fields backed by a data class (elements for
  repeated), and null for maps and well-known types.
- `cardinality`: `OPTIONAL` for nullable properties (`optional`, wrapper types) and oneof members; `IMPLICIT` for other
  singular fields, including message fields, which krotoDC gives a non-null default; `REPEATED`; `MAP`.
- `has`: `OPTIONAL` when non-null (oneof member: its case is selected); `IMPLICIT` when different from the default
  (floating point compared bitwise like protobuf-java, so `-0.0` is set; a message equal to its default instance is not);
  `REPEATED`/`MAP` when non-empty.
- `set(m, null)`: `OPTIONAL` becomes null, `IMPLICIT` takes the constructor's default (a message converted from the proto
  default instance when `allFieldDefaults` is off), collections become empty; a oneof member clears the oneof only if
  its own case is selected.
- The companion's `fields` is a getter onto the `Fields` object, so accessors are built on first use and mutually
  recursive messages can't deadlock or observe a null companion during class initialization.

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

// Copyright 2026 Minsoo Cheong
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.github.mscheong01.krotodc

/**
 * Options passed to the plugin as comma-separated `key=value` pairs, e.g. via the
 * protobuf gradle plugin's `option("allFieldDefaults=true")`.
 */
object KrotoDCOptions {
    val memberConverters: Boolean
        get() = converterLocation == ConverterLocation.MEMBER

    /** Give every data class constructor parameter a default value, as in krotoDC <= 1.1.x. */
    var allFieldDefaults: Boolean = false
        private set

    /**
     * Where converter functions go. `message` (default) puts each top-level message's converters in its own
     * subpackage; `dataClass` puts them in the data class package, which keeps the package count low.
     */
    var converterPackage: ConverterPackage = ConverterPackage.MESSAGE
        private set

    /**
     * Where `toProto`, `toJson` and `fromJson` go. `topLevel` (default) generates top-level extension functions;
     * `member` generates only `toProto` on the data class and `fromProto`/`newProtoBuilder` on its companion, which
     * implement [KrotoDCMessage] and [KrotoDCMessageCompanion]. Those interfaces supply JSON, binary
     * (`toByteArray`/`toByteString`/`parseFrom`) and `descriptor` as default methods built on the three generated members.
     * `toDataClass` stays a top-level extension either way.
     */
    var converterLocation: ConverterLocation = ConverterLocation.TOP_LEVEL
        private set

    /**
     * How converter bodies are written. `builderApply` (default) uses `newBuilder().apply { ... }` with `map`/`mapValues`
     * lambdas. `explicit` writes one statement per field with explicit receivers, explicitly typed locals and plain
     * loops, which leaves kotlinc almost no type inference or scope resolution to do. Requires `converterLocation=member`.
     */
    var converterStyle: ConverterStyle = ConverterStyle.BUILDER_APPLY
        private set

    val explicitConverters: Boolean
        get() = converterStyle == ConverterStyle.EXPLICIT

    /**
     * Give each `<Service>GrpcKroto` object `SERVICE_NAME`, `serviceDescriptor`, a `<method>Method` descriptor per
     * method and `FullMethodNames` constants. Off by default. Requires `converterLocation=member`.
     */
    var grpcDescriptors: Boolean = false
        private set

    /**
     * Generate `object <OuterClassName>Kroto` with the file descriptor and a `KrotoDCExtension` per custom option for
     * each `.proto` file that declares extensions. Off by default. Requires `converterLocation=member`.
     */
    var extensionAccessors: Boolean = false
        private set

    /** Annotate converter functions with `@KrotoDCConverter`. */
    var converterAnnotations: Boolean = true
        private set

    enum class ConverterLocation(val optionValue: String) {
        TOP_LEVEL("topLevel"),
        MEMBER("member")
    }

    enum class ConverterStyle(val optionValue: String) {
        BUILDER_APPLY("builderApply"),
        EXPLICIT("explicit")
    }

    enum class ConverterPackage(val optionValue: String) {
        MESSAGE("message"),
        DATA_CLASS("dataClass")
    }

    fun load(parameter: String) {
        val options = parameter.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { option ->
                val parts = option.split('=', limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "true")
            }
        options.keys.forEach { key ->
            require(key in knownOptions) { "Unknown krotoDC option '$key'. Known options: $knownOptions" }
        }
        allFieldDefaults = options["allFieldDefaults"]?.toBooleanStrict() ?: false
        converterPackage = options["converterPackage"]?.let { value ->
            ConverterPackage.entries.find { it.optionValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown converterPackage '$value'. Known values: ${ConverterPackage.entries.map { it.optionValue }}"
                )
        } ?: ConverterPackage.MESSAGE
        converterLocation = options["converterLocation"]?.let { value ->
            ConverterLocation.entries.find { it.optionValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown converterLocation '$value'. Known values: ${ConverterLocation.entries.map { it.optionValue }}"
                )
        } ?: ConverterLocation.TOP_LEVEL
        converterAnnotations = options["converterAnnotations"]?.toBooleanStrict() ?: true
        converterStyle = options["converterStyle"]?.let { value ->
            ConverterStyle.entries.find { it.optionValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown converterStyle '$value'. Known values: ${ConverterStyle.entries.map { it.optionValue }}"
                )
        } ?: ConverterStyle.BUILDER_APPLY
        require(converterStyle != ConverterStyle.EXPLICIT || converterLocation == ConverterLocation.MEMBER) {
            "converterStyle=explicit requires converterLocation=member"
        }
        grpcDescriptors = options["grpcDescriptors"]?.toBooleanStrict() ?: false
        require(!grpcDescriptors || converterLocation == ConverterLocation.MEMBER) {
            "grpcDescriptors=true requires converterLocation=member"
        }
        extensionAccessors = options["extensionAccessors"]?.toBooleanStrict() ?: false
        require(!extensionAccessors || converterLocation == ConverterLocation.MEMBER) {
            "extensionAccessors=true requires converterLocation=member"
        }
    }

    private val knownOptions = setOf(
        "allFieldDefaults",
        "converterPackage",
        "converterLocation",
        "converterAnnotations",
        "converterStyle",
        "grpcDescriptors",
        "extensionAccessors"
    )
}

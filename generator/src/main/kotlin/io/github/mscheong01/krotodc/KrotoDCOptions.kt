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
     * `member` makes them members of the data class and its companion, which implement [KrotoDCMessage] and
     * [KrotoDCMessageCompanion] (which also adds binary `toByteArray`/`toByteString`/`parseFrom`). `toDataClass` stays
     * a top-level extension either way.
     */
    var converterLocation: ConverterLocation = ConverterLocation.TOP_LEVEL
        private set

    /** Annotate converter functions with `@KrotoDCConverter`. */
    var converterAnnotations: Boolean = true
        private set

    enum class ConverterLocation(val optionValue: String) {
        TOP_LEVEL("topLevel"),
        MEMBER("member")
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
    }

    private val knownOptions = setOf("allFieldDefaults", "converterPackage", "converterLocation", "converterAnnotations")
}

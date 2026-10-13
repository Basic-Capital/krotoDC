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

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat

/**
 * How [KrotoJson.print] and [KrotoDCMessage.toJson] print canonical protobuf JSON. Each flag maps to the
 * [JsonFormat.Printer] method of the same name. The defaults match `JsonFormat.printer()`: pretty-printed, proto3
 * defaults omitted, lowerCamelCase keys.
 */
data class KrotoJsonOptions(
    /** Print fields without presence even when they hold their default value ([JsonFormat.Printer.alwaysPrintFieldsWithNoPresence]). */
    val includeDefaultValues: Boolean = false,
    /** Use the `.proto` field names (snake_case) as keys instead of the lowerCamelCase JSON names. */
    val preservingProtoFieldNames: Boolean = false,
    /** Print compactly, without newlines or indentation. Independent of [includeDefaultValues]. */
    val omittingInsignificantWhitespace: Boolean = false,
    /** Resolves `google.protobuf.Any` payloads. Without one, printing a message that contains an `Any` throws. */
    val typeRegistry: JsonFormat.TypeRegistry? = null
) {
    /** Built once per options instance, so a shared `val` of options doesn't rebuild the printer on each call. */
    internal val printer: JsonFormat.Printer by lazy {
        var printer = JsonFormat.printer()
        if (includeDefaultValues) printer = printer.alwaysPrintFieldsWithNoPresence()
        if (preservingProtoFieldNames) printer = printer.preservingProtoFieldNames()
        if (omittingInsignificantWhitespace) printer = printer.omittingInsignificantWhitespace()
        if (typeRegistry != null) printer = printer.usingTypeRegistry(typeRegistry)
        printer
    }

    companion object {
        /** `JsonFormat.printer()`, which is what `toJson()` prints. */
        @JvmField
        val DEFAULT = KrotoJsonOptions()

        /** Defaults included and compact, which is what `toJson(includeDefaultValues = true)` prints. */
        @JvmField
        val INCLUDE_DEFAULT_VALUES = KrotoJsonOptions(includeDefaultValues = true, omittingInsignificantWhitespace = true)
    }
}

/** How [KrotoJson.merge] and [KrotoDCMessageCompanion.fromJson] parse protobuf JSON. */
data class KrotoJsonParseOptions(
    /** Skip keys that aren't fields of the message. With `false`, an unknown key throws. */
    val ignoringUnknownFields: Boolean = true,
    /** Resolves `google.protobuf.Any` payloads. */
    val typeRegistry: JsonFormat.TypeRegistry? = null
) {
    internal val parser: JsonFormat.Parser by lazy {
        var parser = JsonFormat.parser()
        if (ignoringUnknownFields) parser = parser.ignoringUnknownFields()
        if (typeRegistry != null) parser = parser.usingTypeRegistry(typeRegistry)
        parser
    }

    companion object {
        /** Unknown fields ignored, which is how `fromJson(json)` parses. */
        @JvmField
        val LENIENT = KrotoJsonParseOptions()

        /** Unknown fields rejected, like `JsonFormat.parser()`. */
        @JvmField
        val STRICT = KrotoJsonParseOptions(ignoringUnknownFields = false)
    }
}

/** Shared JSON conversion for generated `toJson`/`fromJson`, so each data class doesn't carry its own copy. */
object KrotoJson {
    fun print(proto: Message, includeDefaultValues: Boolean): String =
        print(proto, if (includeDefaultValues) KrotoJsonOptions.INCLUDE_DEFAULT_VALUES else KrotoJsonOptions.DEFAULT)

    fun print(proto: Message, options: KrotoJsonOptions): String = options.printer.print(proto)

    /** Merges [json] into [builder], ignoring unknown fields. Throws [com.google.protobuf.InvalidProtocolBufferException] on malformed JSON. */
    fun <B : Message.Builder> merge(json: String, builder: B): B = merge(json, builder, KrotoJsonParseOptions.LENIENT)

    /** Merges [json] into [builder]. Throws [com.google.protobuf.InvalidProtocolBufferException] on malformed JSON, or on unknown fields when strict. */
    fun <B : Message.Builder> merge(json: String, builder: B, options: KrotoJsonParseOptions): B {
        options.parser.merge(json, builder)
        return builder
    }
}

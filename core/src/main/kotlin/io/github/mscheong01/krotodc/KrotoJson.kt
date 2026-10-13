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

/** Shared JSON conversion for generated `toJson`/`fromJson`, so each data class doesn't carry its own copy. */
object KrotoJson {
    private val printer: JsonFormat.Printer = JsonFormat.printer()
    private val printerWithDefaults: JsonFormat.Printer =
        JsonFormat.printer().alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace()
    private val parser: JsonFormat.Parser = JsonFormat.parser().ignoringUnknownFields()

    fun print(proto: Message, includeDefaultValues: Boolean): String =
        if (includeDefaultValues) printerWithDefaults.print(proto) else printer.print(proto)

    fun <B : Message.Builder> merge(json: String, builder: B): B {
        parser.merge(json, builder)
        return builder
    }
}

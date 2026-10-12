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
    /** Give every data class constructor parameter a default value, as in krotoDC <= 1.1.x. */
    var allFieldDefaults: Boolean = false
        private set

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
    }

    private val knownOptions = setOf("allFieldDefaults")
}

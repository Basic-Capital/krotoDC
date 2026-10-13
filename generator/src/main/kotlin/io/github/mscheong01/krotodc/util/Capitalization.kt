// Copyright 2023 Minsoo Cheong
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
package io.github.mscheong01.krotodc.util

fun String.capitalize(): String {
    return this.replaceFirstChar { it.uppercase() }
}
fun String.decapitalize(): String {
    return this.replaceFirstChar { it.lowercase() }
}

/** `UnaryRPC` -> `UNARY_RPC`, `GetHTTPThing` -> `GET_HTTP_THING`, `listPeople` -> `LIST_PEOPLE`. */
fun String.toUpperSnakeCase(): String {
    val result = StringBuilder()
    for ((i, c) in this.withIndex()) {
        if (c.isUpperCase() && i > 0 && this[i - 1] != '_') {
            val previous = this[i - 1]
            val next = this.getOrNull(i + 1)
            if (previous.isLowerCase() || previous.isDigit() || (previous.isUpperCase() && next != null && next.isLowerCase())) {
                result.append('_')
            }
        }
        result.append(c.uppercaseChar())
    }
    return result.toString()
}

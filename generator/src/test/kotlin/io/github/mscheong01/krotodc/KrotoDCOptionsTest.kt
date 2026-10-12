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

import com.example.defaultvaluestest.DefaultValuesTest
import io.github.mscheong01.krotodc.specgenerators.file.DataClassGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KrotoDCOptionsTest {

    @AfterEach
    fun reset() {
        KrotoDCOptions.load("")
    }

    private fun generatedDataClasses(): String =
        DataClassGenerator().generate(DefaultValuesTest.getDescriptor()).joinToString("\n") { it.toString() }

    @Test
    fun `non-optional fields have no default by default`() {
        KrotoDCOptions.load("")
        Assertions.assertThat(generatedDataClasses())
            .contains("public val intField: Int,")
            .doesNotContain("intField: Int = 0")
    }

    @Test
    fun `allFieldDefaults gives every field a default`() {
        KrotoDCOptions.load("allFieldDefaults=true")
        Assertions.assertThat(generatedDataClasses())
            .contains("intField: Int = 0")
            .contains("stringList: List<String> = listOf()")
    }

    @Test
    fun `unknown option is rejected`() {
        assertThrows<IllegalArgumentException> { KrotoDCOptions.load("allFieldDefault=true") }
    }
}

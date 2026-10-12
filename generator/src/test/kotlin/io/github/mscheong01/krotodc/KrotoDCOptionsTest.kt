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
import com.google.protobuf.compiler.PluginProtos
import io.github.mscheong01.krotodc.specgenerators.file.ConverterGenerator
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
    fun `converters get a subpackage per message by default`() {
        KrotoDCOptions.load("")
        val packages = ConverterGenerator().generate(DefaultValuesTest.getDescriptor()).map { it.packageName }
        Assertions.assertThat(packages).containsExactlyInAnyOrder(
            "com.example.defaultvaluestest.krotodc.defaultvaluetest",
            "com.example.defaultvaluestest.krotodc.defaultvaluenestedmessage"
        )
    }

    @Test
    fun `converterPackage=dataClass puts converters in the data class package`() {
        KrotoDCOptions.load("converterPackage=dataClass")
        val fileSpecs = ConverterGenerator().generate(DefaultValuesTest.getDescriptor())
        Assertions.assertThat(fileSpecs.map { it.packageName }.toSet())
            .containsExactly("com.example.defaultvaluestest.krotodc")
        Assertions.assertThat(fileSpecs.joinToString("\n") { it.toString() })
            .doesNotContain("import com.example.defaultvaluestest.krotodc.defaultvaluenestedmessage")
    }

    @Test
    fun `unknown converterPackage is rejected`() {
        assertThrows<IllegalArgumentException> { KrotoDCOptions.load("converterPackage=file") }
    }

    @Test
    fun `only files in file_to_generate are generated`() {
        val fileProto = DefaultValuesTest.getDescriptor().toProto()
        val request = PluginProtos.CodeGeneratorRequest.newBuilder()
            .addProtoFile(fileProto)
            .build()
        val out = java.io.ByteArrayOutputStream()
        val stdout = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            KrotoDCCodeGenerator.generateCode(request)
        } finally {
            System.setOut(stdout)
        }
        Assertions.assertThat(PluginProtos.CodeGeneratorResponse.parseFrom(out.toByteArray()).fileList).isEmpty()
    }

    @Test
    fun `files in file_to_generate are generated`() {
        val fileProto = DefaultValuesTest.getDescriptor().toProto()
        val request = PluginProtos.CodeGeneratorRequest.newBuilder()
            .addProtoFile(fileProto)
            .addFileToGenerate(fileProto.name)
            .build()
        val out = java.io.ByteArrayOutputStream()
        val stdout = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            KrotoDCCodeGenerator.generateCode(request)
        } finally {
            System.setOut(stdout)
        }
        Assertions.assertThat(PluginProtos.CodeGeneratorResponse.parseFrom(out.toByteArray()).fileList).isNotEmpty()
    }

    @Test
    fun `unknown option is rejected`() {
        assertThrows<IllegalArgumentException> { KrotoDCOptions.load("allFieldDefault=true") }
    }
}

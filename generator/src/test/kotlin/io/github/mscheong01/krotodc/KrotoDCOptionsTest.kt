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
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.compiler.PluginProtos
import io.github.mscheong01.krotodc.specgenerators.file.ConverterGenerator
import io.github.mscheong01.krotodc.specgenerators.file.DataClassGenerator
import io.github.mscheong01.krotodc.specgenerators.file.GrpcKrotoGenerator
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
    fun `toJson keeps the let lambda on the same line however KotlinPoet wraps`() {
        KrotoDCOptions.load("converterPackage=dataClass")
        // The line before `{` is 99 chars for an 8-char message name; try a range of lengths around it.
        val messages = (4..14).map { length ->
            DescriptorProtos.DescriptorProto.newBuilder().setName("M" + "x".repeat(length - 1)).build()
        }
        val fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("wrap_test.proto")
            .setPackage("wraptest")
            .setSyntax("proto3")
            .addAllMessageType(messages)
            .build()
        val fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileProto, arrayOf())
        val code = ConverterGenerator().generate(fileDescriptor).joinToString("\n") { it.toString() }
        val letLines = code.lines().filter { "JsonFormat.printer().let" in it }
        Assertions.assertThat(letLines).hasSize(messages.size).allMatch { "let { defaultPrinter ->" in it }
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

    private val explicitTestFiles = listOf(
        DefaultValuesTest.getDescriptor(),
        io.github.mscheong01.test.Test.getDescriptor(),
        io.github.mscheong01.keyword.Keyword.getDescriptor(),
        io.github.mscheong01.wellknowntypes.WellKnownTypes.getDescriptor()
    )

    private fun generatedMemberCode(): String = explicitTestFiles.flatMap { file ->
        DataClassGenerator().generate(file) + ConverterGenerator().generate(file)
    }.joinToString("\n") { it.toString() }

    @Test
    fun `converterStyle=explicit converters have no scope functions or collection lambdas`() {
        KrotoDCOptions.load("converterLocation=member,converterStyle=explicit")
        val code = generatedMemberCode()
        Assertions.assertThat(code)
            .doesNotContain(".apply {", ".map {", ".mapValues {", ".let {", "this@")
            .contains("val protoBuilder: ", "return protoBuilder.build()", "for (element in this.", "for (entry in this.")
            .contains("ArrayList<", "LinkedHashMap<", "if (this.has")
    }

    @Test
    fun `converterStyle defaults to builderApply`() {
        KrotoDCOptions.load("converterLocation=member")
        Assertions.assertThat(generatedMemberCode()).contains(".apply {", ".map {", ".mapValues {").doesNotContain("protoBuilder")
    }

    private fun generatedGrpcCode(): String =
        GrpcKrotoGenerator().generate(io.github.mscheong01.rpc.Rpc.getDescriptor()).joinToString("\n") { it.toString() }

    @Test
    fun `gRPC descriptor accessors are generated only with grpcDescriptors=true`() {
        val accessors = arrayOf("SERVICE_NAME", "serviceDescriptor", "unaryTestMethod", "FullMethodNames")
        KrotoDCOptions.load("converterLocation=member")
        Assertions.assertThat(generatedGrpcCode()).doesNotContain(*accessors)
        KrotoDCOptions.load("converterLocation=member,grpcDescriptors=true")
        Assertions.assertThat(generatedGrpcCode()).contains(*accessors)
        assertThrows<IllegalArgumentException> { KrotoDCOptions.load("grpcDescriptors=true") }
    }

    @Test
    fun `converterStyle=explicit requires member converters and unknown styles are rejected`() {
        assertThrows<IllegalArgumentException> { KrotoDCOptions.load("converterStyle=explicit") }
        assertThrows<IllegalArgumentException> { KrotoDCOptions.load("converterLocation=member,converterStyle=fancy") }
    }
}

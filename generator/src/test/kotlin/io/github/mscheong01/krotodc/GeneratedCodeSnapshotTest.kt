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

import com.google.protobuf.Descriptors
import com.google.protobuf.compiler.PluginProtos
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.security.MessageDigest

/**
 * Pins the code generated without `fieldAccessors` (in both converter locations) to a SHA-256 per generated file,
 * recorded from the 1.2.2-basic.11 plugin jar, so opt-in features can't change the default output.
 *
 * To re-record after an intended change to the generated code, point `KROTODC_SNAPSHOT_GENERATOR_JAR` at the plugin jar
 * to record from (e.g. `generator/build/libs/generator-<version>-jdk8.jar` for the current code):
 * `KROTODC_SNAPSHOT_GENERATOR_JAR=... ./gradlew :generator:test --tests '*GeneratedCodeSnapshotTest*'`. That run
 * feeds the same requests to the jar, fails if its output differs from this build's, and rewrites [SNAPSHOT_PATH].
 */
class GeneratedCodeSnapshotTest {

    @AfterEach
    fun reset() {
        KrotoDCOptions.load("")
    }

    private val files = listOf(
        com.example.defaultvaluestest.DefaultValuesTest.getDescriptor(),
        io.github.mscheong01.test.Test.getDescriptor(),
        io.github.mscheong01.keyword.Keyword.getDescriptor(),
        io.github.mscheong01.wellknowntypes.WellKnownTypes.getDescriptor(),
        io.github.mscheong01.rpc.Rpc.getDescriptor(),
        io.github.mscheong01.importtest.ImportFromOtherFileTest.getDescriptor(),
        com.example.importtest.OuterClassNameTestProto.getDescriptor(),
        com.example.application.system.types.VeryLongMessageNameProto.getDescriptor()
    )

    private val optionSets = listOf(
        "",
        "allFieldDefaults=true",
        "allFieldDefaults=true,fieldAccessors=false",
        "converterPackage=dataClass,converterAnnotations=false",
        "converterLocation=member",
        MEMBER_OPTIONS,
        "$MEMBER_OPTIONS,converterStyle=explicit",
        "$MEMBER_OPTIONS,converterStyle=explicit,fieldAccessors=false"
    )

    private fun request(parameter: String): PluginProtos.CodeGeneratorRequest {
        val ordered = LinkedHashSet<Descriptors.FileDescriptor>()
        fun visit(file: Descriptors.FileDescriptor) {
            if (file in ordered) return
            file.dependencies.forEach { visit(it) }
            ordered.add(file)
        }
        files.forEach { visit(it) }
        return PluginProtos.CodeGeneratorRequest.newBuilder()
            .setParameter(parameter)
            .addAllProtoFile(ordered.map { it.toProto() })
            .addAllFileToGenerate(files.map { it.name })
            .build()
    }

    private fun files(response: ByteArray): Map<String, String> =
        PluginProtos.CodeGeneratorResponse.parseFrom(response).also {
            Assertions.assertThat(it.hasError()).describedAs(it.error).isFalse
        }.fileList.associate { it.name to it.content }

    private fun generateInProcess(request: PluginProtos.CodeGeneratorRequest): Map<String, String> {
        val out = ByteArrayOutputStream()
        val stdout = System.out
        System.setOut(PrintStream(out))
        try {
            KrotoDCCodeGenerator.generateCode(request)
        } finally {
            System.setOut(stdout)
        }
        return files(out.toByteArray())
    }

    private fun generateWithJar(jar: String, request: PluginProtos.CodeGeneratorRequest): Map<String, String> {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-jar", jar).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        process.outputStream.use { request.writeTo(it) }
        val response = process.inputStream.readBytes()
        Assertions.assertThat(process.waitFor()).isEqualTo(0)
        return files(response)
    }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun manifest(generated: Map<String, Map<String, String>>): String =
        generated.flatMap { (options, files) ->
            files.map { (name, content) -> "${options.ifEmpty { "-" }} $name ${sha256(content)}" }
        }.sorted().joinToString("\n", postfix = "\n")

    @Test
    fun `code generated without fieldAccessors matches the snapshot`() {
        val generated = optionSets.associateWith { generateInProcess(request(it)) }
        Assertions.assertThat(generated.values).allMatch { it.size > 50 }

        val recordFrom = System.getenv("KROTODC_SNAPSHOT_GENERATOR_JAR")
        if (recordFrom != null) {
            val fromJar = optionSets.associateWith { generateWithJar(recordFrom, request(it)) }
            File(SNAPSHOT_PATH).apply { parentFile.mkdirs() }.writeText(manifest(fromJar))
            for (options in optionSets) {
                Assertions.assertThat(generated.getValue(options)).describedAs(options).isEqualTo(fromJar.getValue(options))
            }
        }
        val snapshot = if (recordFrom != null) {
            File(SNAPSHOT_PATH).readText()
        } else {
            javaClass.getResource("/" + File(SNAPSHOT_PATH).name)!!.readText()
        }
        Assertions.assertThat(manifest(generated).lines()).containsExactlyElementsOf(snapshot.lines())
    }

    @Test
    fun `fieldAccessors adds to the generated code without changing what is there`() {
        for (options in listOf("allFieldDefaults=true", MEMBER_OPTIONS)) {
            val off = generateInProcess(request(options))
            val on = generateInProcess(request("$options,fieldAccessors=true"))
            Assertions.assertThat(on.keys).describedAs(options).isEqualTo(off.keys)
            for ((name, content) in off) {
                val withAccessors = on.getValue(name)
                if (withAccessors == content) continue
                Assertions.assertThat(withAccessors).describedAs("$options $name").contains("KrotoDCField<")
                // Every line of the default output is still there, in order.
                val remaining = withAccessors.lines().iterator()
                for (line in content.lines()) {
                    Assertions.assertThat(generateSequence { if (remaining.hasNext()) remaining.next() else null }.any { it == line })
                        .describedAs("$options $name: $line")
                        .isTrue
                }
            }
        }
    }

    companion object {
        const val SNAPSHOT_PATH = "src/test/resources/generated-code-without-field-accessors.sha256"
        const val MEMBER_OPTIONS =
            "converterLocation=member,converterAnnotations=false,allFieldDefaults=true,grpcDescriptors=true,extensionAccessors=true"
    }
}

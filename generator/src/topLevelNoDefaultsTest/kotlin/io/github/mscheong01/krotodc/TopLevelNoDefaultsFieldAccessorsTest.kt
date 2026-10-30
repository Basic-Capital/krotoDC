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

import com.example.memberfields.MemberFields
import com.example.memberfields.krotodc.Catalog
import com.example.memberfields.krotodc.Document
import com.example.memberfields.krotodc.Folder
import com.example.memberfields.krotodc.Ledger
import com.example.memberfields.krotodc.ledger.toDataClass
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Int32Value
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import com.example.memberfields.krotodc.catalog.toDataClass as catalogToDataClass
import com.example.memberfields.krotodc.folder.toDataClass as folderToDataClass

/**
 * Code generated with top-level converters and `fieldAccessors=true` but without `allFieldDefaults`, from the memberTest
 * and test protos, so data classes have no no-arg constructors and implicit message fields reset to the data class
 * converted from the proto default instance.
 */
class TopLevelNoDefaultsFieldAccessorsTest {

    private val files = listOf(
        MemberFields.getDescriptor(),
        com.example.membertest.MemberTest.getDescriptor(),
        com.example.membertest.options.MemberOptions.getDescriptor(),
        com.example.defaultvaluestest.DefaultValuesTest.getDescriptor(),
        io.github.mscheong01.test.Test.getDescriptor(),
        io.github.mscheong01.keyword.Keyword.getDescriptor(),
        io.github.mscheong01.wellknowntypes.WellKnownTypes.getDescriptor(),
        io.github.mscheong01.rpc.Rpc.getDescriptor(),
        io.github.mscheong01.importtest.ImportFromOtherFileTest.getDescriptor(),
        com.example.importtest.OuterClassNameTestProto.getDescriptor(),
        com.example.application.system.types.VeryLongMessageNameProto.getDescriptor()
    )

    private fun allMessages(messages: List<Descriptors.Descriptor>): List<Descriptors.Descriptor> =
        messages.filter { !it.options.mapEntry }.flatMap { listOf(it) + allMessages(it.nestedTypes) }

    /**
     * The `Fields.all` (or `KrotoFields.all`, ...) list of the data class generated for [descriptor], found by
     * reflection so every message of every test proto is covered; empty if the message has no fields, which get no
     * accessor object.
     */
    private fun fieldsOf(descriptor: Descriptors.Descriptor): List<KrotoDCField<*, *>> {
        val file = descriptor.file
        val javaPackage = file.options.javaPackage.ifEmpty { file.`package` }
        val simpleNames = descriptor.fullName.removePrefix(file.`package` + ".").replace('.', '$')
        val dataClass = Class.forName("$javaPackage.krotodc.$simpleNames")
        val accessorObjects = dataClass.declaredClasses.filter { nested ->
            Regex("(Kroto)?Fields\\d*").matches(nested.simpleName) && nested.declaredFields.any { it.name == "INSTANCE" }
        }
        if (descriptor.fields.isEmpty()) {
            Assertions.assertThat(accessorObjects).describedAs(descriptor.fullName).isEmpty()
            return emptyList()
        }
        val accessorObject = accessorObjects.single()
        val list = accessorObject.declaredFields.single { Regex("all_*").matches(it.name) }
        @Suppress("UNCHECKED_CAST")
        return list.get(null) as List<KrotoDCField<*, *>>
    }

    @Test
    fun `fields match the descriptor's fields for every message of every test proto`() {
        val messages = files.flatMap { allMessages(it.messageTypes) }
        Assertions.assertThat(messages.size).isGreaterThan(40)
        for (descriptor in messages) {
            val fields = fieldsOf(descriptor)
            Assertions.assertThat(fields.map { it.number to it.jsonName })
                .describedAs(descriptor.fullName)
                .containsExactlyElementsOf(descriptor.fields.map { it.number to it.jsonName })
            fields.zip(descriptor.fields).forEach { (field, fieldDescriptor) ->
                val name = "${descriptor.fullName}.${field.name}"
                Assertions.assertThat(field.name).isEqualTo(fieldDescriptor.name)
                Assertions.assertThat(field.kind.name).describedAs(name).isEqualTo(
                    when (fieldDescriptor.javaType) {
                        Descriptors.FieldDescriptor.JavaType.BOOLEAN -> "BOOL"
                        Descriptors.FieldDescriptor.JavaType.BYTE_STRING -> "BYTES"
                        else -> fieldDescriptor.javaType.name
                    }
                )
                Assertions.assertThat(field.oneofName).describedAs(name).isEqualTo(fieldDescriptor.realContainingOneof?.name)
                Assertions.assertThat(field.cardinality == KrotoDCCardinality.MAP).describedAs(name).isEqualTo(fieldDescriptor.isMapField)
                Assertions.assertThat(field.cardinality == KrotoDCCardinality.REPEATED).describedAs(name)
                    .isEqualTo(fieldDescriptor.isRepeated && !fieldDescriptor.isMapField)
                Assertions.assertThat(field.messageCompanion).describedAs(name).isNull()
                val messageType = fieldDescriptor
                    .takeIf { it.javaType == Descriptors.FieldDescriptor.JavaType.MESSAGE && !it.isMapField }
                    ?.messageType?.takeIf { !it.file.name.startsWith("google/") }
                if (messageType == null) {
                    Assertions.assertThat(field.messageFields as Any?).describedAs(name).isNull()
                } else if (messageType.fields.isEmpty()) {
                    Assertions.assertThat(field.messageFields!!()).describedAs(name).isEmpty()
                } else {
                    Assertions.assertThat(field.messageFields!!()).describedAs(name).isSameAs(fieldsOf(messageType))
                }
            }
        }
    }

    private val empty: Ledger = MemberFields.Ledger.getDefaultInstance().toDataClass()

    private val fullProto: MemberFields.Ledger = MemberFields.Ledger.newBuilder()
        .setId("x").setCount(-1).setTotal(2L).setRatio(-0.0).setWeight(2f).setActive(true)
        .setToken(ByteString.copyFromUtf8("t")).setStatus(MemberFields.Status.CLOSED)
        .setNickname("").setLimit(0).setPreviousStatus(MemberFields.Status.STATUS_UNSPECIFIED)
        .setOwner(MemberFields.Ledger.Owner.newBuilder().setName("o"))
        .addTags("").addCoOwners(MemberFields.Ledger.Owner.getDefaultInstance())
        .putBalances("", 0L).putOwnersByRole("admin", MemberFields.Ledger.Owner.getDefaultInstance())
        .setEmail("").setLate("l")
        .setOpenedAt(Timestamp.newBuilder().setSeconds(5)).setGrace(com.google.protobuf.Duration.newBuilder().setSeconds(1))
        .setWrappedLimit(Int32Value.of(0)).addWaits(com.google.protobuf.Duration.getDefaultInstance())
        .setExtra(com.google.protobuf.Any.pack(MemberFields.Ledger.Owner.getDefaultInstance()))
        .addHistory(MemberFields.Status.STATUS_UNSPECIFIED)
        .setBackupOwner(MemberFields.Ledger.Owner.getDefaultInstance())
        .build()

    @Test
    fun `get, has and set round-trip and clear without constructor defaults`() {
        val full = fullProto.toDataClass()
        val proto: Message = fullProto
        for (field in Ledger.Fields.all) {
            val descriptor = MemberFields.Ledger.getDescriptor().findFieldByNumber(field.number)
            val protoHas = if (descriptor.isRepeated) proto.getRepeatedFieldCount(descriptor) > 0 else proto.hasField(descriptor)
            Assertions.assertThat(field.has(full)).describedAs(field.name).isEqualTo(protoHas)
            Assertions.assertThat(field.has(empty)).describedAs(field.name).isFalse
            @Suppress("UNCHECKED_CAST")
            val copied = (field as KrotoDCField<Ledger, Any>).set(empty, field.get(full))
            Assertions.assertThat(field.get(copied)).describedAs(field.name).isEqualTo(field.get(full))
        }
        Assertions.assertThat(Ledger.Fields.all.fold(full) { message, field -> field.set(message, null) }).isEqualTo(empty)
        // An implicit message field resets to the converted default instance, which has no fields set.
        val cleared = Ledger.Fields.owner.set(full, null)
        Assertions.assertThat(cleared.owner).isEqualTo(MemberFields.Ledger.Owner.getDefaultInstance().toDataClass())
        Assertions.assertThat(Ledger.Fields.owner.has(cleared)).isFalse
        Assertions.assertThat(Ledger.Fields.email.get(full)).isEqualTo("")
        Assertions.assertThat(Ledger.Fields.email.set(full, null).contact).isNull()
        Assertions.assertThat(Ledger.Fields.delegate.set(full, null)).isEqualTo(full)
    }

    @Test
    fun `the Fields name collision holds without constructor defaults`() {
        val catalog = MemberFields.Catalog.newBuilder()
            .setFields(MemberFields.Catalog.Fields.newBuilder().setLabel("l"))
            .build()
            .catalogToDataClass()
        Assertions.assertThat(Catalog.KrotoFields.fields.has(catalog)).isTrue
        Assertions.assertThat(Catalog.Fields.KrotoFields.label.get(catalog.fields)).isEqualTo("l")
        val cleared = Catalog.KrotoFields.fields.set(catalog, null)
        Assertions.assertThat(cleared).isEqualTo(MemberFields.Catalog.getDefaultInstance().catalogToDataClass())
        Assertions.assertThat(Catalog.KrotoFields.fields.messageFields!!()).isSameAs(Catalog.Fields.KrotoFields.all)
    }

    @Test
    fun `recursive messages are walked through messageFields`() {
        val tree = MemberFields.Folder.newBuilder()
            .setName("root")
            .addDocuments(MemberFields.Document.newBuilder().setTitle("a").addLinks(MemberFields.Folder.newBuilder().setName("sub")))
            .build()
            .folderToDataClass()
        Assertions.assertThat(Folder.Fields.documents.messageFields!!()).isSameAs(Document.Fields.all)
        Assertions.assertThat(Document.Fields.parent.messageFields!!()).isSameAs(Folder.Fields.all)
        Assertions.assertThat(walk(Folder.Fields.all, tree))
            .containsExactly("Folder.name", "Folder.documents", "Document.title", "Document.links", "Folder.name")
    }

    private fun walk(fields: List<KrotoDCField<*, *>>, message: Any): List<String> {
        @Suppress("UNCHECKED_CAST")
        val typed = fields as List<KrotoDCField<Any, *>>
        return typed.filter { it.has(message) }.flatMap { field ->
            val values = when (val value = field.get(message)) {
                is List<*> -> value
                else -> listOf(value)
            }
            val nested = field.messageFields?.invoke()
            listOf("${message.javaClass.simpleName}.${field.name}") +
                if (nested == null) emptyList() else values.flatMap { walk(nested, it!!) }
        }
    }
}

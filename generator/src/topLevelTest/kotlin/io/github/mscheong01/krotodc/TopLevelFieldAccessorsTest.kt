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
import com.example.memberfields.MemberFields.Status
import com.example.memberfields.krotodc.Catalog
import com.example.memberfields.krotodc.Document
import com.example.memberfields.krotodc.Folder
import com.example.memberfields.krotodc.Ledger
import com.example.memberfields.krotodc.Picker
import com.example.memberfields.krotodc.ledger.toProto
import com.example.membertest.MemberTest
import com.example.membertest.MemberTest.Color
import com.example.membertest.krotodc.AllShapes
import com.example.membertest.krotodc.Envelope
import com.example.membertest.krotodc.Person
import com.example.membertest.options.MemberOptions
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.example.membertest.options.krotodc.Account as OptionsAccount
import com.example.membertest.options.krotodc.Tag as OptionsTag

/**
 * MemberFieldAccessorsTest's assertions for code generated with the default top-level converters
 * (`allFieldDefaults=true,fieldAccessors=true`), where data classes don't implement [KrotoDCMessage], `Fields.all` is
 * the entry point and [KrotoDCField.messageCompanion] is always null.
 */
class TopLevelFieldAccessorsTest {

    private val time = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

    /**
     * Every message of the test protos with its accessors and protoc descriptor. Messages without fields have no
     * accessor object. [every proto message is listed here] checks it is complete.
     */
    private val messages: List<Pair<List<KrotoDCField<*, *>>, Descriptors.Descriptor>> = listOf(
        Ledger.Fields.all to MemberFields.Ledger.getDescriptor(),
        Ledger.Owner.Fields.all to MemberFields.Ledger.Owner.getDescriptor(),
        Catalog.KrotoFields.all to MemberFields.Catalog.getDescriptor(),
        Catalog.Fields.KrotoFields.all to MemberFields.Catalog.Fields.getDescriptor(),
        Picker.KrotoFields.all to MemberFields.Picker.getDescriptor(),
        Folder.Fields.all to MemberFields.Folder.getDescriptor(),
        Document.Fields.all to MemberFields.Document.getDescriptor(),
        emptyList<KrotoDCField<*, *>>() to MemberFields.NoFields.getDescriptor(),
        Person.Fields.all to MemberTest.Person.getDescriptor(),
        Person.Address.Fields.all to MemberTest.Person.Address.getDescriptor(),
        emptyList<KrotoDCField<*, *>>() to MemberTest.Empty.getDescriptor(),
        Envelope.Fields.all to MemberTest.Envelope.getDescriptor(),
        AllShapes.Fields.all to MemberTest.AllShapes.getDescriptor(),
        OptionsTag.Fields.all to MemberOptions.Tag.getDescriptor(),
        OptionsAccount.Fields.all to MemberOptions.Account.getDescriptor()
    )

    private val files = listOf(MemberFields.getDescriptor(), MemberTest.getDescriptor(), MemberOptions.getDescriptor())

    private fun allMessages(messages: List<Descriptors.Descriptor>): List<Descriptors.Descriptor> =
        messages.filter { !it.options.mapEntry }.flatMap { listOf(it) + allMessages(it.nestedTypes) }

    @Test
    fun `every proto message is listed here`() {
        Assertions.assertThat(messages.map { it.second.fullName })
            .containsExactlyInAnyOrderElementsOf(files.flatMap { allMessages(it.messageTypes) }.map { it.fullName })
    }

    @Test
    fun `fields match the descriptor's fields for every message`() {
        val byDescriptor = messages.associate { (fields, descriptor) -> descriptor to fields }
        for ((fields, descriptor) in messages) {
            val descriptorFields = descriptor.fields
            Assertions.assertThat(fields.map { it.number to it.jsonName })
                .describedAs(descriptor.fullName)
                .containsExactlyElementsOf(descriptorFields.map { it.number to it.jsonName })
            fields.zip(descriptorFields).forEach { (field, fieldDescriptor) ->
                Assertions.assertThat(field.name).isEqualTo(fieldDescriptor.name)
                Assertions.assertThat(field.kind.name).isEqualTo(
                    when (fieldDescriptor.javaType) {
                        Descriptors.FieldDescriptor.JavaType.BOOLEAN -> "BOOL"
                        Descriptors.FieldDescriptor.JavaType.BYTE_STRING -> "BYTES"
                        else -> fieldDescriptor.javaType.name
                    }
                )
                Assertions.assertThat(field.oneofName).isEqualTo(fieldDescriptor.realContainingOneof?.name)
                Assertions.assertThat(field.cardinality == KrotoDCCardinality.MAP).isEqualTo(fieldDescriptor.isMapField)
                Assertions.assertThat(field.cardinality == KrotoDCCardinality.REPEATED)
                    .isEqualTo(fieldDescriptor.isRepeated && !fieldDescriptor.isMapField)
                Assertions.assertThat(field.messageCompanion).isNull()
                // messageFields leads to the accessors of the field's own message type, if it has a data class.
                val messageType = fieldDescriptor
                    .takeIf { it.javaType == Descriptors.FieldDescriptor.JavaType.MESSAGE && !it.isMapField }
                    ?.messageType?.takeIf { !it.file.name.startsWith("google/") }
                if (messageType == null) {
                    Assertions.assertThat(field.messageFields as Any?).describedAs(field.name).isNull()
                } else {
                    Assertions.assertThat(field.messageFields!!()).describedAs(field.name).isSameAs(byDescriptor.getValue(messageType))
                }
            }
        }
    }

    @Test
    fun `Ledger fields are in declaration order with their metadata`() {
        data class Expected(
            val name: String,
            val jsonName: String,
            val number: Int,
            val kind: KrotoDCFieldKind,
            val cardinality: KrotoDCCardinality,
            val oneofName: String? = null,
            val messageFields: List<KrotoDCField<*, *>>? = null
        )
        val i = KrotoDCCardinality.IMPLICIT
        val o = KrotoDCCardinality.OPTIONAL
        val r = KrotoDCCardinality.REPEATED
        val m = KrotoDCCardinality.MAP
        val owner = Ledger.Owner.Fields.all
        val expected = listOf(
            Expected("id", "id", 1, KrotoDCFieldKind.STRING, i),
            Expected("count", "count", 2, KrotoDCFieldKind.INT, i),
            Expected("total", "total", 3, KrotoDCFieldKind.LONG, i),
            Expected("ratio", "ratio", 4, KrotoDCFieldKind.DOUBLE, i),
            Expected("weight", "weight", 5, KrotoDCFieldKind.FLOAT, i),
            Expected("active", "active", 6, KrotoDCFieldKind.BOOL, i),
            Expected("token", "token", 7, KrotoDCFieldKind.BYTES, i),
            Expected("status", "status", 8, KrotoDCFieldKind.ENUM, i),
            Expected("nickname", "nickname", 9, KrotoDCFieldKind.STRING, o),
            Expected("limit", "limit", 10, KrotoDCFieldKind.INT, o),
            Expected("previous_status", "previousStatus", 11, KrotoDCFieldKind.ENUM, o),
            Expected("owner", "owner", 12, KrotoDCFieldKind.MESSAGE, i, messageFields = owner),
            Expected("tags", "tags", 13, KrotoDCFieldKind.STRING, r),
            Expected("co_owners", "coOwners", 14, KrotoDCFieldKind.MESSAGE, r, messageFields = owner),
            Expected("balances", "balances", 15, KrotoDCFieldKind.MESSAGE, m),
            Expected("owners_by_role", "ownersByRole", 16, KrotoDCFieldKind.MESSAGE, m),
            Expected("email", "email", 17, KrotoDCFieldKind.STRING, o, "contact"),
            Expected("delegate", "delegate", 18, KrotoDCFieldKind.MESSAGE, o, "contact", owner),
            Expected("contact_after", "contactAfter", 19, KrotoDCFieldKind.MESSAGE, o, "contact"),
            Expected("late", "late", 40, KrotoDCFieldKind.STRING, i),
            Expected("opened_at", "openedAt", 20, KrotoDCFieldKind.MESSAGE, i),
            Expected("grace", "grace", 21, KrotoDCFieldKind.MESSAGE, i),
            Expected("wrapped_limit", "wrappedLimit", 22, KrotoDCFieldKind.MESSAGE, o),
            Expected("waits", "waits", 23, KrotoDCFieldKind.MESSAGE, r),
            Expected("extra", "extra", 24, KrotoDCFieldKind.MESSAGE, i),
            Expected("history", "history", 25, KrotoDCFieldKind.ENUM, r),
            Expected("backup_owner", "backupOwner", 26, KrotoDCFieldKind.MESSAGE, o, messageFields = owner)
        )
        Assertions.assertThat(
            Ledger.Fields.all.map {
                Expected(it.name, it.jsonName, it.number, it.kind, it.cardinality, it.oneofName, it.messageFields?.invoke())
            }
        ).containsExactlyElementsOf(expected)
        Assertions.assertThat(Ledger.Fields.all.map { it.propertyName }).containsExactlyElementsOf(expected.map { it.jsonName })
        Assertions.assertThat(Ledger.Fields.all[1]).isSameAs(Ledger.Fields.count)
        Assertions.assertThat(Ledger.Fields.all.map { it.messageCompanion }).containsOnlyNulls()
        Assertions.assertThat(Ledger.Fields.delegate.messageFields!!()).isSameAs(Ledger.Owner.Fields.all)
    }

    @Test
    fun `value types are the data class property types`() {
        // Compile-time checks: each declaration only compiles if the generated V is exactly this type.
        val id: KrotoDCField<Ledger, String> = Ledger.Fields.id
        val token: KrotoDCField<Ledger, ByteString> = Ledger.Fields.token
        val status: KrotoDCField<Ledger, Status> = Ledger.Fields.status
        val limit: KrotoDCField<Ledger, Int> = Ledger.Fields.limit
        val owner: KrotoDCField<Ledger, Ledger.Owner> = Ledger.Fields.owner
        val coOwners: KrotoDCField<Ledger, List<Ledger.Owner>> = Ledger.Fields.coOwners
        val balances: KrotoDCField<Ledger, Map<String, Long>> = Ledger.Fields.balances
        val ownersByRole: KrotoDCField<Ledger, Map<String, Ledger.Owner>> = Ledger.Fields.ownersByRole
        val email: KrotoDCField<Ledger, String> = Ledger.Fields.email
        val delegate: KrotoDCField<Ledger, Ledger.Owner> = Ledger.Fields.delegate
        val contactAfter: KrotoDCField<Ledger, LocalDateTime> = Ledger.Fields.contactAfter
        val openedAt: KrotoDCField<Ledger, LocalDateTime> = Ledger.Fields.openedAt
        val grace: KrotoDCField<Ledger, Duration> = Ledger.Fields.grace
        val wrappedLimit: KrotoDCField<Ledger, Int> = Ledger.Fields.wrappedLimit
        val waits: KrotoDCField<Ledger, List<Duration>> = Ledger.Fields.waits
        val extra: KrotoDCField<Ledger, com.google.protobuf.Any> = Ledger.Fields.extra
        val history: KrotoDCField<Ledger, List<Status>> = Ledger.Fields.history
        val nothing: KrotoDCField<AllShapes, Unit> = AllShapes.Fields.nothing
        val keyword: KrotoDCField<AllShapes, String> = AllShapes.Fields.`class`
        Assertions.assertThat(Ledger.Fields.all).containsAll(
            listOf(
                id, token, status, limit, owner, coOwners, balances, ownersByRole, email, delegate, contactAfter,
                openedAt, grace, wrappedLimit, waits, extra, history
            )
        )
        Assertions.assertThat(nothing.get(AllShapes())).isEqualTo(Unit)
        Assertions.assertThat(keyword.set(AllShapes(), "c").`class`).isEqualTo("c")
    }

    @Test
    fun `implicit scalars use proto3 presence and reset to defaults`() {
        val empty = Ledger()
        val implicitScalars = Ledger.Fields.all.filter {
            it.cardinality == KrotoDCCardinality.IMPLICIT && it.kind != KrotoDCFieldKind.MESSAGE
        }
        Assertions.assertThat(implicitScalars.map { it.name })
            .containsExactly("id", "count", "total", "ratio", "weight", "active", "token", "status", "late")
        implicitScalars.forEach { Assertions.assertThat(it.has(empty)).describedAs(it.name).isFalse }

        val set = Ledger.Fields.id.set(empty, "L1")
            .let { Ledger.Fields.count.set(it, 3) }
            .let { Ledger.Fields.total.set(it, 4L) }
            .let { Ledger.Fields.ratio.set(it, 0.5) }
            .let { Ledger.Fields.weight.set(it, 1.5f) }
            .let { Ledger.Fields.active.set(it, true) }
            .let { Ledger.Fields.token.set(it, ByteString.copyFromUtf8("t")) }
            .let { Ledger.Fields.status.set(it, Status.ACTIVE) }
            .let { Ledger.Fields.late.set(it, "late") }
        Assertions.assertThat(set).isEqualTo(
            Ledger(
                id = "L1", count = 3, total = 4L, ratio = 0.5, weight = 1.5f, active = true,
                token = ByteString.copyFromUtf8("t"), status = Status.ACTIVE, late = "late"
            )
        )
        implicitScalars.forEach { Assertions.assertThat(it.has(set)).describedAs(it.name).isTrue }
        Assertions.assertThat(Ledger.Fields.ratio.get(set)).isEqualTo(0.5)
        Assertions.assertThat(Ledger.Fields.token.get(set)).isEqualTo(ByteString.copyFromUtf8("t"))
        Assertions.assertThat(clearAll(implicitScalars, set)).isEqualTo(empty)
        // Setting the default counts as clearing, like proto3; -0.0 differs from 0.0 bitwise and is present.
        Assertions.assertThat(Ledger.Fields.count.has(Ledger.Fields.count.set(set, 0))).isFalse
        Assertions.assertThat(Ledger.Fields.status.has(Ledger(status = Status.STATUS_UNSPECIFIED))).isFalse
        Assertions.assertThat(Ledger.Fields.ratio.has(Ledger(ratio = -0.0))).isTrue
        Assertions.assertThat(Ledger.Fields.weight.has(Ledger(weight = -0.0f))).isTrue
    }

    @Test
    fun `optional fields have explicit presence`() {
        val zero = Ledger(nickname = "", limit = 0, previousStatus = Status.STATUS_UNSPECIFIED, wrappedLimit = 0)
        for (field in listOf(Ledger.Fields.nickname, Ledger.Fields.limit, Ledger.Fields.previousStatus, Ledger.Fields.wrappedLimit)) {
            Assertions.assertThat(field.has(Ledger())).describedAs(field.name).isFalse
            Assertions.assertThat(field.get(Ledger())).describedAs(field.name).isNull()
            Assertions.assertThat(field.has(zero)).describedAs(field.name).isTrue
        }
        Assertions.assertThat(Ledger.Fields.limit.get(zero)).isEqualTo(0)
        Assertions.assertThat(Ledger.Fields.wrappedLimit.set(Ledger(), 7).wrappedLimit).isEqualTo(7)
        Assertions.assertThat(Ledger.Fields.nickname.set(zero, null).nickname).isNull()
        val backup = Ledger.Owner(name = "b")
        Assertions.assertThat(Ledger.Fields.backupOwner.set(Ledger(), backup).backupOwner).isEqualTo(backup)
        Assertions.assertThat(Ledger.Fields.backupOwner.has(Ledger(backupOwner = Ledger.Owner()))).isTrue
        Assertions.assertThat(Ledger.Fields.backupOwner.set(Ledger(backupOwner = backup), null)).isEqualTo(Ledger())
    }

    @Test
    fun `messages and well-known types reset to the constructor default`() {
        val owner = Ledger.Owner(name = "Ada", age = 36)
        val withOwner = Ledger.Fields.owner.set(Ledger(), owner)
        Assertions.assertThat(withOwner.owner).isEqualTo(owner)
        Assertions.assertThat(Ledger.Fields.owner.get(withOwner)).isEqualTo(owner)
        Assertions.assertThat(Ledger.Fields.owner.has(withOwner)).isTrue
        Assertions.assertThat(Ledger.Fields.owner.has(Ledger())).isFalse
        Assertions.assertThat(Ledger.Fields.owner.set(withOwner, null)).isEqualTo(Ledger())

        val wkt = Ledger(openedAt = time, grace = Duration.ofSeconds(5), extra = com.google.protobuf.Any.pack(owner.toProto()))
        for (field in listOf(Ledger.Fields.openedAt, Ledger.Fields.grace, Ledger.Fields.extra)) {
            Assertions.assertThat(field.has(Ledger())).describedAs(field.name).isFalse
            Assertions.assertThat(field.has(wkt)).describedAs(field.name).isTrue
            Assertions.assertThat(field.set(wkt, null)).describedAs(field.name).isNotEqualTo(wkt)
        }
        Assertions.assertThat(Ledger.Fields.openedAt.get(wkt)).isEqualTo(time)
        Assertions.assertThat(Ledger.Fields.grace.set(wkt, Duration.ofMinutes(1)).grace).isEqualTo(Duration.ofMinutes(1))
        Assertions.assertThat(clearAll(Ledger.Fields.all, wkt)).isEqualTo(Ledger())
    }

    @Test
    fun `repeated and map fields are present when non-empty`() {
        val full = Ledger(
            tags = listOf("a"),
            coOwners = listOf(Ledger.Owner(name = "c")),
            balances = mapOf("usd" to 5L),
            ownersByRole = mapOf("admin" to Ledger.Owner(name = "a")),
            waits = listOf(Duration.ofMillis(1)),
            history = listOf(Status.CLOSED)
        )
        val collections = Ledger.Fields.all.filter {
            it.cardinality == KrotoDCCardinality.REPEATED || it.cardinality == KrotoDCCardinality.MAP
        }
        Assertions.assertThat(collections.map { it.name })
            .containsExactly("tags", "co_owners", "balances", "owners_by_role", "waits", "history")
        collections.forEach {
            Assertions.assertThat(it.has(Ledger())).describedAs(it.name).isFalse
            Assertions.assertThat(it.has(full)).describedAs(it.name).isTrue
        }
        Assertions.assertThat(clearAll(collections, full)).isEqualTo(Ledger())
        Assertions.assertThat(Ledger.Fields.balances.get(full)).isEqualTo(mapOf("usd" to 5L))
        Assertions.assertThat(Ledger.Fields.tags.set(full, listOf("x", "y")).tags).containsExactly("x", "y")
        Assertions.assertThat(Ledger.Fields.tags.has(Ledger.Fields.tags.set(full, emptyList()))).isFalse
    }

    @Test
    fun `oneof members read, select and clear their own case`() {
        val delegate = Ledger.Owner(name = "Del")
        val byEmail = Ledger.Fields.email.set(Ledger(), "a@b.c")
        Assertions.assertThat(byEmail.contact).isEqualTo(Ledger.Contact.Email("a@b.c"))
        Assertions.assertThat(Ledger.Fields.email.get(byEmail)).isEqualTo("a@b.c")
        Assertions.assertThat(Ledger.Fields.email.has(byEmail)).isTrue
        Assertions.assertThat(Ledger.Fields.delegate.get(byEmail)).isNull()
        Assertions.assertThat(Ledger.Fields.delegate.has(byEmail)).isFalse

        val byDelegate = Ledger.Fields.delegate.set(byEmail, delegate)
        Assertions.assertThat(byDelegate.contact).isEqualTo(Ledger.Contact.Delegate(delegate))
        Assertions.assertThat(Ledger.Fields.delegate.get(byDelegate)).isEqualTo(delegate)
        Assertions.assertThat(Ledger.Fields.email.has(byDelegate)).isFalse
        Assertions.assertThat(Ledger.Fields.email.get(byDelegate)).isNull()

        // Clearing another case leaves the oneof alone; clearing the selected case clears it.
        Assertions.assertThat(Ledger.Fields.email.set(byDelegate, null)).isEqualTo(byDelegate)
        Assertions.assertThat(Ledger.Fields.delegate.set(byDelegate, null)).isEqualTo(Ledger())

        val byTime = Ledger.Fields.contactAfter.set(byDelegate, time)
        Assertions.assertThat(byTime.contact).isEqualTo(Ledger.Contact.ContactAfter(time))
        Assertions.assertThat(Ledger.Fields.contactAfter.get(byTime)).isEqualTo(time)
        // A selected case is present even with its default value, as in proto.
        Assertions.assertThat(Ledger.Fields.email.has(Ledger(contact = Ledger.Contact.Email("")))).isTrue

        val hue = AllShapes.Fields.hue.set(AllShapes(), Color.BLUE)
        Assertions.assertThat(hue.choice).isEqualTo(AllShapes.Choice.Hue(Color.BLUE))
        Assertions.assertThat(AllShapes.Fields.at.has(hue)).isFalse
    }

    @Test
    fun `has agrees with the protobuf message wherever krotoDC keeps presence`() {
        val samples = listOf(
            Ledger(),
            Ledger(
                id = "x", count = -1, ratio = -0.0, weight = 2f, token = ByteString.copyFromUtf8("t"), status = Status.CLOSED,
                nickname = "", limit = 0, previousStatus = Status.STATUS_UNSPECIFIED, tags = listOf(""),
                balances = mapOf("" to 0L), contact = Ledger.Contact.Email(""), late = "l", wrappedLimit = 0,
                waits = listOf(Duration.ZERO), history = listOf(Status.STATUS_UNSPECIFIED), backupOwner = Ledger.Owner()
            ),
            Ledger(contact = Ledger.Contact.Delegate(Ledger.Owner())),
            Ledger(contact = Ledger.Contact.ContactAfter(time))
        )
        for (sample in samples) {
            val proto: Message = sample.toProto()
            for (field in Ledger.Fields.all) {
                val descriptor = MemberFields.Ledger.getDescriptor().findFieldByNumber(field.number)
                val protoHas = if (descriptor.isRepeated) proto.getRepeatedFieldCount(descriptor) > 0 else proto.hasField(descriptor)
                // Singular message fields are always set by toProto, since the data class has no way to leave them unset.
                if (field.cardinality == KrotoDCCardinality.IMPLICIT && field.kind == KrotoDCFieldKind.MESSAGE) {
                    Assertions.assertThat(protoHas).isTrue
                } else {
                    Assertions.assertThat(field.has(sample)).describedAs("${field.name} in $sample").isEqualTo(protoHas)
                }
            }
        }
    }

    /** Uses only the `Fields.all` list, as code that is generic over messages would. */
    private fun <D : Any> clearAll(fields: List<KrotoDCField<D, *>>, message: D): D =
        fields.fold(message) { cleared, field -> field.set(cleared, null) }

    @Test
    fun `a nested type named Fields moves the accessors to KrotoFields`() {
        val label = Catalog.Fields(label = "l")
        val catalog = Catalog.KrotoFields.fields.set(Catalog(), label)
        Assertions.assertThat(catalog.fields).isEqualTo(label)
        Assertions.assertThat(Catalog.KrotoFields.all.map { it.propertyName }).containsExactly("fields", "extra")
        Assertions.assertThat(Catalog.KrotoFields.fields.messageFields!!()).isSameAs(Catalog.Fields.KrotoFields.all)
        Assertions.assertThat(Catalog.KrotoFields.extra.messageFields!!()).isSameAs(Catalog.Fields.KrotoFields.all)
        // The nested message is itself named Fields, so its accessor object can't be Fields either.
        Assertions.assertThat(Catalog.Fields.KrotoFields.label.get(label)).isEqualTo("l")
        Assertions.assertThat(Catalog.Fields.KrotoFields.all).containsExactly(Catalog.Fields.KrotoFields.label)
        // A oneof named fields generates a sealed interface Fields.
        val picked = Picker.KrotoFields.index.set(Picker(), 0)
        Assertions.assertThat(picked.fields).isEqualTo(Picker.Fields.Index(0))
        Assertions.assertThat(Picker.KrotoFields.index.has(picked)).isTrue
        Assertions.assertThat(Picker.KrotoFields.text.get(picked)).isNull()
        Assertions.assertThat(Picker.KrotoFields.all.map { it.oneofName }).containsExactly("fields", "fields")
    }

    @Test
    fun `mutually recursive messages reference each other's fields`() {
        Assertions.assertThat(Folder.Fields.documents.messageFields!!()).isSameAs(Document.Fields.all)
        Assertions.assertThat(Folder.Fields.readme.messageFields!!()).isSameAs(Document.Fields.all)
        Assertions.assertThat(Document.Fields.parent.messageFields!!()).isSameAs(Folder.Fields.all)
        Assertions.assertThat(Document.Fields.links.messageFields!!()).isSameAs(Folder.Fields.all)
        val tree = Folder(
            name = "root",
            documents = listOf(Document(title = "a", links = listOf(Folder(name = "sub")))),
            readme = Document(title = "r", parent = Folder())
        )
        val walked = walk("Folder", Folder.Fields.all, tree)
        Assertions.assertThat(walked).containsExactly(
            "Folder.name",
            "Folder.documents",
            "Document.title",
            "Document.links",
            "Folder.name",
            "Folder.readme",
            "Document.title",
            "Document.parent"
        )
    }

    /** Depth-first names of the present fields, following [KrotoDCField.messageFields] into nested messages. */
    private fun walk(name: String, fields: List<KrotoDCField<*, *>>, message: Any): List<String> {
        @Suppress("UNCHECKED_CAST")
        val typed = fields as List<KrotoDCField<Any, *>>
        return typed.filter { it.has(message) }.flatMap { field ->
            val values = when (val value = field.get(message)) {
                is List<*> -> value
                else -> listOf(value)
            }
            val nested = field.messageFields?.invoke()
            listOf("$name.${field.name}") +
                if (nested == null) emptyList() else values.flatMap { walk(it!!.javaClass.simpleName, nested, it) }
        }
    }

    @Test
    fun `initializing recursive accessors concurrently in a fresh class loader neither deadlocks nor sees nulls`() {
        val classpath = listOf(
            Folder::class.java,
            MemberFields::class.java,
            KrotoDCField::class.java,
            Message::class.java,
            Unit::class.java,
            com.google.protobuf.util.JsonFormat::class.java
        ).map { it.protectionDomain.codeSource.location }.distinct().toTypedArray()
        val folder = Folder::class.java.name
        val document = Document::class.java.name
        // Each entry point reads one message's accessors first, then follows messageFields into the other's.
        val entryPoints = listOf<(ClassLoader) -> List<Any?>>(
            { loader -> objectFields(loader, folder) },
            { loader -> objectFields(loader, document) },
            { loader -> nestedFields(loader, folder) },
            { loader -> nestedFields(loader, document) }
        )
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(25) { iteration ->
                val loader = URLClassLoader(classpath, null)
                val barrier = CyclicBarrier(2)
                val first = entryPoints[iteration % entryPoints.size]
                val second = entryPoints[(iteration / entryPoints.size + iteration + 1) % entryPoints.size]
                val results = listOf(first, second).map { entryPoint ->
                    executor.submit(
                        Callable {
                            barrier.await()
                            entryPoint(loader)
                        }
                    )
                }.map { it.get(10, TimeUnit.SECONDS) }
                for (fields in results) {
                    Assertions.assertThat(fields).hasSize(3).doesNotContainNull()
                    val messageFields = fields.filter { invoke(it, "getKind").toString() == "MESSAGE" }
                    Assertions.assertThat(messageFields).hasSize(2)
                    messageFields.forEach { field ->
                        Assertions.assertThat(invokeMessageFields(loader, field)).hasSize(3).doesNotContainNull()
                    }
                }
                loader.close()
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun invoke(target: Any?, method: String): Any? = target!!.javaClass.getMethod(method).invoke(target)

    private fun objectFields(loader: ClassLoader, className: String): List<Any?> =
        Class.forName("$className\$Fields", true, loader).getField("all").get(null) as List<Any?>

    /** The fields of the other message, reached through the first MESSAGE field of [className]. */
    private fun nestedFields(loader: ClassLoader, className: String): List<Any?> =
        invokeMessageFields(loader, objectFields(loader, className).first { invoke(it, "getKind").toString() == "MESSAGE" })

    /** Calls `field.messageFields!!()` through [loader]'s own kotlin `Function0`, which isn't this test's. */
    private fun invokeMessageFields(loader: ClassLoader, field: Any?): List<Any?> {
        val function = invoke(field, "getMessageFields")!!
        return loader.loadClass("kotlin.jvm.functions.Function0").getMethod("invoke").invoke(function) as List<Any?>
    }
}

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

import com.example.membertest.MemberTest
import com.example.membertest.MemberTest.Color
import com.example.membertest.krotodc.Empty
import com.example.membertest.krotodc.Person
import com.example.membertest.krotodc.person.toDataClass
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Timestamp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MemberConvertersTest {

    private val person = Person(
        name = "Ada",
        age = 36,
        nickname = "countess",
        home = Person.Address(city = "London"),
        previous = listOf(Person.Address(city = "Marylebone")),
        byLabel = mapOf("work" to Person.Address(city = "Ockham")),
        counters = mapOf("letters" to 3L),
        favoriteColor = Color.BLUE,
        born = LocalDateTime.of(1815, 12, 10, 0, 0),
        contact = Person.Contact.Mailing(Person.Address(city = "Somerset"))
    )

    @Test
    fun `toProto is a member and round trips through toDataClass`() {
        val proto: MemberTest.Person = person.toProto()
        Assertions.assertThat(proto.name).isEqualTo("Ada")
        Assertions.assertThat(proto.mailing.city).isEqualTo("Somerset")
        Assertions.assertThat(proto.toDataClass()).isEqualTo(person)
    }

    @Test
    fun `data classes and companions implement the runtime interfaces`() {
        Assertions.assertThat(person).isInstanceOf(KrotoDCMessage::class.java)
        Assertions.assertThat(Person.Address(city = "x")).isInstanceOf(KrotoDCMessage::class.java)
        Assertions.assertThat(Person).isInstanceOf(KrotoDCMessageCompanion::class.java)
        Assertions.assertThat(Empty).isInstanceOf(KrotoDCMessageCompanion::class.java)
    }

    @Test
    fun `companion converts from proto and json`() {
        Assertions.assertThat(Person.fromProto(person.toProto())).isEqualTo(person)
        Assertions.assertThat(Person.fromJson(person.toJson())).isEqualTo(person)
        Assertions.assertThat(Person.fromJson(person.toJson(includeDefaultValues = true))).isEqualTo(person)
        Assertions.assertThat(Person.newProtoBuilder()).isInstanceOf(MemberTest.Person.Builder::class.java)
    }

    @Test
    fun `a Class is enough to convert without kotlin reflection`() {
        val clazz: Class<*> = Person.Address::class.java
        val companion = clazz.getField("Companion").get(null) as KrotoDCMessageCompanion<*, *>
        val json = (Person.Address(city = "Paris") as KrotoDCMessage<*>).toJson()
        val builder = companion.newProtoBuilder()
        KrotoJson.merge(json, builder)
        Assertions.assertThat(companion.fromProtoUnchecked(builder.build())).isEqualTo(Person.Address(city = "Paris"))
    }

    @Test
    fun `toJson matches JsonFormat`() {
        Assertions.assertThat(person.toJson())
            .isEqualTo(com.google.protobuf.util.JsonFormat.printer().print(person.toProto()))
        Assertions.assertThat(person.toJson(includeDefaultValues = true)).isEqualTo(
            com.google.protobuf.util.JsonFormat.printer().alwaysPrintFieldsWithNoPresence()
                .omittingInsignificantWhitespace().print(person.toProto())
        )
    }

    /** The same message as [person], built with the protoc-java builder rather than through krotoDC. */
    private val javaPerson: MemberTest.Person = MemberTest.Person.newBuilder()
        .setName("Ada")
        .setAge(36)
        .setNickname("countess")
        .setHome(MemberTest.Person.Address.newBuilder().setCity("London"))
        .addPrevious(MemberTest.Person.Address.newBuilder().setCity("Marylebone"))
        .putByLabel("work", MemberTest.Person.Address.newBuilder().setCity("Ockham").build())
        .putCounters("letters", 3L)
        .setFavoriteColor(Color.BLUE)
        .setBorn(Timestamp.newBuilder().setSeconds(person.toProto().born.seconds))
        .setMailing(MemberTest.Person.Address.newBuilder().setCity("Somerset"))
        .build()

    @Test
    fun `toByteArray and toByteString round trip through parseFrom`() {
        Assertions.assertThat(Person.parseFrom(person.toByteArray())).isEqualTo(person)
        Assertions.assertThat(Person.parseFrom(person.toByteString())).isEqualTo(person)
        val address = Person.Address(city = "Paris")
        Assertions.assertThat(Person.Address.parseFrom(address.toByteArray())).isEqualTo(address)
        Assertions.assertThat(Person.parseFrom(Person().toByteArray())).isEqualTo(Person())
    }

    @Test
    fun `binary encoding is byte identical to the java proto encoding`() {
        Assertions.assertThat(person.toProto()).isEqualTo(javaPerson)
        Assertions.assertThat(person.toByteArray()).containsExactly(*javaPerson.toByteArray())
        Assertions.assertThat(person.toByteString()).isEqualTo(javaPerson.toByteString())
        Assertions.assertThat(Person.Address().toByteArray()).isEmpty()
        Assertions.assertThat(Empty().toByteString()).isEqualTo(ByteString.EMPTY)
    }

    @Test
    fun `non-optional message fields are always written, as toProto sets them`() {
        val defaultBytes = MemberTest.Person.getDefaultInstance().toByteArray()
        Assertions.assertThat(defaultBytes).isEmpty()
        Assertions.assertThat(Person.parseFrom(defaultBytes)).isEqualTo(Person())
        val home = MemberTest.Person.HOME_FIELD_NUMBER shl 3 or 2
        val born = MemberTest.Person.BORN_FIELD_NUMBER shl 3 or 2
        Assertions.assertThat(Person().toByteArray()).containsExactly(home.toByte(), 0, born.toByte(), 0)
    }

    @Test
    fun `parseFrom decodes bytes produced by the java proto`() {
        Assertions.assertThat(Person.parseFrom(javaPerson.toByteArray())).isEqualTo(person)
        Assertions.assertThat(Person.parseFrom(javaPerson.toByteString())).isEqualTo(person)
        val emailOnly = MemberTest.Person.newBuilder().setName("Grace").setEmail("grace@example.com").build()
        Assertions.assertThat(Person.parseFrom(emailOnly.toByteArray()))
            .isEqualTo(Person(name = "Grace", contact = Person.Contact.Email("grace@example.com")))
        Assertions.assertThat(Empty.parseFrom(MemberTest.Empty.getDefaultInstance().toByteArray())).isInstanceOf(Empty::class.java)
    }

    @Test
    fun `parseFrom skips unknown fields and rejects malformed input`() {
        val withUnknownField = javaPerson.toBuilder()
            .setUnknownFields(
                com.google.protobuf.UnknownFieldSet.newBuilder()
                    .addField(99, com.google.protobuf.UnknownFieldSet.Field.newBuilder().addVarint(7).build())
                    .build()
            )
            .build()
        Assertions.assertThat(Person.parseFrom(withUnknownField.toByteArray())).isEqualTo(person)
        Assertions.assertThatThrownBy { Person.parseFrom(byteArrayOf(0x0A, 0x05, 0x41)) }
            .isInstanceOf(InvalidProtocolBufferException::class.java)
    }

    @Test
    fun `binary serialization works through the runtime interfaces`() {
        val companion = Person::class.java.getField("Companion").get(null) as KrotoDCMessageCompanion<*, *>
        val bytes = (person as KrotoDCMessage<*>).toByteArray()
        Assertions.assertThat(companion.parseFrom(bytes)).isEqualTo(person)
        Assertions.assertThat(companion.parseFrom(ByteString.copyFrom(bytes))).isEqualTo(person)
    }

    @Test
    fun `converterAnnotations=false leaves converters unannotated`() {
        val facade = Class.forName("com.example.membertest.krotodc.person.PersonConvertersKt")
        Assertions.assertThat(facade.methods.filter { it.name == "toDataClass" }).isNotEmpty
            .allMatch { it.getAnnotation(KrotoDCConverter::class.java) == null }
        Assertions.assertThat(facade.methods.map { it.name }).doesNotContain("toProto", "toJson", "fromJson")
    }
}

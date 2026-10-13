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

    @Test
    fun `converterAnnotations=false leaves converters unannotated`() {
        val facade = Class.forName("com.example.membertest.krotodc.person.PersonConvertersKt")
        Assertions.assertThat(facade.methods.filter { it.name == "toDataClass" }).isNotEmpty
            .allMatch { it.getAnnotation(KrotoDCConverter::class.java) == null }
        Assertions.assertThat(facade.methods.map { it.name }).doesNotContain("toProto", "toJson", "fromJson")
    }
}

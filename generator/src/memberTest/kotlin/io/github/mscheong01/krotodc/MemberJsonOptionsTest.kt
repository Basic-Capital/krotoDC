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
import com.example.membertest.krotodc.Envelope
import com.example.membertest.krotodc.Person
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.util.JsonFormat
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MemberJsonOptionsTest {

    /** Leaves `age` and `counters` at their defaults so includeDefaultValues has something to add. */
    private val person = Person(
        name = "Ada",
        nickname = "countess",
        home = Person.Address(city = "London"),
        byLabel = mapOf("work" to Person.Address(city = "Ockham")),
        favoriteColor = MemberTest.Color.BLUE
    )

    private val proto: MemberTest.Person = person.toProto()

    @Test
    fun `each option matches the JsonFormat printer method of the same name`() {
        Assertions.assertThat(person.toJson(KrotoJsonOptions())).isEqualTo(JsonFormat.printer().print(proto))
        Assertions.assertThat(person.toJson(KrotoJsonOptions(includeDefaultValues = true)))
            .isEqualTo(JsonFormat.printer().alwaysPrintFieldsWithNoPresence().print(proto))
            .contains("\n  \"age\": 0,")
        Assertions.assertThat(person.toJson(KrotoJsonOptions(preservingProtoFieldNames = true)))
            .isEqualTo(JsonFormat.printer().preservingProtoFieldNames().print(proto))
            .contains("\"by_label\"", "\"favorite_color\"")
        Assertions.assertThat(person.toJson(KrotoJsonOptions(omittingInsignificantWhitespace = true)))
            .isEqualTo(JsonFormat.printer().omittingInsignificantWhitespace().print(proto))
            .doesNotContain("\n", "\"age\"")
    }

    @Test
    fun `options combine`() {
        val webhookStyle = KrotoJsonOptions(includeDefaultValues = true, preservingProtoFieldNames = true)
        Assertions.assertThat(person.toJson(webhookStyle)).isEqualTo(
            JsonFormat.printer().alwaysPrintFieldsWithNoPresence().preservingProtoFieldNames().print(proto)
        )
        val all = KrotoJsonOptions(
            includeDefaultValues = true,
            preservingProtoFieldNames = true,
            omittingInsignificantWhitespace = true
        )
        Assertions.assertThat(person.toJson(all)).isEqualTo(
            JsonFormat.printer().alwaysPrintFieldsWithNoPresence().preservingProtoFieldNames()
                .omittingInsignificantWhitespace().print(proto)
        )
    }

    @Test
    fun `the boolean toJson is unchanged and equals the predefined options`() {
        Assertions.assertThat(person.toJson()).isEqualTo(person.toJson(KrotoJsonOptions.DEFAULT))
        Assertions.assertThat(person.toJson(includeDefaultValues = true))
            .isEqualTo(person.toJson(KrotoJsonOptions.INCLUDE_DEFAULT_VALUES))
            .isEqualTo(
                JsonFormat.printer().alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace().print(proto)
            )
        Assertions.assertThat(KrotoJson.print(proto, KrotoJsonOptions(includeDefaultValues = true)))
            .isEqualTo(person.toJson(KrotoJsonOptions(includeDefaultValues = true)))
    }

    @Test
    fun `toJson with options works through the runtime interface`() {
        val message: KrotoDCMessage<*> = person
        Assertions.assertThat(message.toJson(KrotoJsonOptions(preservingProtoFieldNames = true)))
            .isEqualTo(JsonFormat.printer().preservingProtoFieldNames().print(proto))
    }

    @Test
    fun `typeRegistry resolves Any when printing and parsing`() {
        val envelope = Envelope(senderName = "Ada", payload = com.google.protobuf.Any.pack(proto))
        Assertions.assertThatThrownBy { envelope.toJson() }.isInstanceOf(InvalidProtocolBufferException::class.java)

        val registry = JsonFormat.TypeRegistry.newBuilder().add(MemberTest.Person.getDescriptor()).build()
        val json = envelope.toJson(KrotoJsonOptions(typeRegistry = registry))
        Assertions.assertThat(json)
            .isEqualTo(JsonFormat.printer().usingTypeRegistry(registry).print(envelope.toProto()))
            .contains("\"@type\": \"type.googleapis.com/com.example.membertest.Person\"")

        Assertions.assertThatThrownBy { Envelope.fromJson(json) }.isInstanceOf(InvalidProtocolBufferException::class.java)
        val parsed = Envelope.fromJson(json, KrotoJsonParseOptions(typeRegistry = registry))
        Assertions.assertThat(parsed).isEqualTo(envelope)
        Assertions.assertThat(parsed.payload.unpack(MemberTest.Person::class.java)).isEqualTo(proto)
    }

    @Test
    fun `fromJson is lenient by default and strict on request`() {
        val withUnknown = """{"name": "Ada", "notAField": 1}"""
        Assertions.assertThat(Person.fromJson(withUnknown)).isEqualTo(Person(name = "Ada"))
        Assertions.assertThat(Person.fromJson(withUnknown, KrotoJsonParseOptions.LENIENT)).isEqualTo(Person(name = "Ada"))
        Assertions.assertThatThrownBy { Person.fromJson(withUnknown, KrotoJsonParseOptions.STRICT) }
            .isInstanceOf(InvalidProtocolBufferException::class.java)
            .hasMessageContaining("notAField")
        Assertions.assertThatThrownBy { JsonFormat.parser().merge(withUnknown, MemberTest.Person.newBuilder()) }
            .isInstanceOf(InvalidProtocolBufferException::class.java)
    }

    @Test
    fun `strict fromJson round trips every print option`() {
        val printOptions = listOf(
            KrotoJsonOptions(),
            KrotoJsonOptions(includeDefaultValues = true),
            KrotoJsonOptions(preservingProtoFieldNames = true, omittingInsignificantWhitespace = true),
            KrotoJsonOptions(includeDefaultValues = true, preservingProtoFieldNames = true)
        )
        for (options in printOptions) {
            Assertions.assertThat(Person.fromJson(person.toJson(options), KrotoJsonParseOptions.STRICT)).isEqualTo(person)
        }
    }

    @Test
    fun `fromJson with options works through the runtime interface`() {
        val companion = Person::class.java.getField("Companion").get(null) as KrotoDCMessageCompanion<*, *>
        Assertions.assertThat(companion.fromJson(person.toJson(), KrotoJsonParseOptions.STRICT)).isEqualTo(person)
        Assertions.assertThatThrownBy { companion.fromJson("""{"bogus": true}""", KrotoJsonParseOptions.STRICT) }
            .isInstanceOf(InvalidProtocolBufferException::class.java)
    }

    @Test
    fun `KrotoJson merge takes parse options`() {
        val strictBuilder = MemberTest.Person.newBuilder()
        Assertions.assertThatThrownBy { KrotoJson.merge("""{"bogus": true}""", strictBuilder, KrotoJsonParseOptions.STRICT) }
            .isInstanceOf(InvalidProtocolBufferException::class.java)
        val built = KrotoJson.merge("""{"name": "Ada", "bogus": true}""", MemberTest.Person.newBuilder()).build()
        Assertions.assertThat(built.name).isEqualTo("Ada")
    }
}

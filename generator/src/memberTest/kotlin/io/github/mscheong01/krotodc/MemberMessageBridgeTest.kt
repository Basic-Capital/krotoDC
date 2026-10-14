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

import com.example.membertest.krotodc.Empty
import com.example.membertest.krotodc.Person
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MemberMessageBridgeTest {
    private val person = Person(
        name = "Ada",
        age = 36,
        nickname = "A",
        home = Person.Address(city = "London"),
        contact = Person.Contact.Email(email = "ada@example.com")
    )

    @Test
    fun `toMessage is a runtime message equal to toProto`() {
        val message: Message = person.toMessage()
        Assertions.assertThat(message).isEqualTo(person.toProto())
        Assertions.assertThat(message.descriptorForType).isSameAs(Person.descriptor)
    }

    @Test
    fun `fromMessage reads a dynamic message`() {
        val dynamic = DynamicMessage.parseFrom(Person.descriptor, person.toByteArray())
        Assertions.assertThat(Person.fromMessage(dynamic)).isEqualTo(person)
    }

    @Test
    fun `fromMessage reads an edited copy of toMessage`() {
        val edited = person.toMessage().toBuilder()
            .clearField(Person.descriptor.findFieldByName("nickname"))
            .build()
        Assertions.assertThat(Person.fromMessage(edited)).isEqualTo(person.copy(nickname = null))
    }

    @Test
    fun `fromMessage rejects a message of another type`() {
        assertThrows<IllegalArgumentException> { Person.fromMessage(Empty().toMessage()) }
    }

    @Test
    fun `fromMessage works through the runtime interfaces`() {
        val companion = Person::class.java.getField("Companion").get(null) as KrotoDCMessageCompanion<*, *>
        val message = (person as KrotoDCMessage<*>).toMessage()
        Assertions.assertThat(companion.fromMessage(message)).isEqualTo(person)
    }
}

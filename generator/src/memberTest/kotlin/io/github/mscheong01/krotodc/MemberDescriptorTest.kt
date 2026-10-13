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
import com.example.membertest.krotodc.Empty
import com.example.membertest.krotodc.Envelope
import com.example.membertest.krotodc.Person
import com.google.protobuf.util.JsonFormat
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MemberDescriptorTest {

    @Test
    fun `companion descriptor is the protobuf descriptor of the message`() {
        Assertions.assertThat(Person.descriptor).isSameAs(MemberTest.Person.getDescriptor())
        Assertions.assertThat(Person.Address.descriptor).isSameAs(MemberTest.Person.Address.getDescriptor())
        Assertions.assertThat(Empty.descriptor).isSameAs(MemberTest.Empty.getDescriptor())
        Assertions.assertThat(Person.Address.descriptor.fullName).isEqualTo("com.example.membertest.Person.Address")
        Assertions.assertThat(Person.descriptor.findFieldByName("by_label").jsonName).isEqualTo("byLabel")
    }

    @Test
    fun `descriptor file is the file descriptor`() {
        Assertions.assertThat(Person.descriptor.file).isSameAs(MemberTest.getDescriptor())
        Assertions.assertThat(Person.descriptor.file.name).isEqualTo("member_test.proto")
        Assertions.assertThat(Person.descriptor.file.messageTypes.map { it.name })
            .contains("Person", "Empty", "Envelope")
    }

    @Test
    fun `descriptor is reachable through the runtime interface`() {
        val companion = Person.Address::class.java.getField("Companion").get(null) as KrotoDCMessageCompanion<*, *>
        Assertions.assertThat(companion.descriptor).isSameAs(MemberTest.Person.Address.getDescriptor())
        Assertions.assertThat(companion.newProtoBuilder().descriptorForType).isSameAs(companion.descriptor)
    }

    @Test
    fun `a TypeRegistry can be built from companions alone`() {
        val registry = JsonFormat.TypeRegistry.newBuilder().add(Person.descriptor).build()
        val envelope = Envelope(payload = com.google.protobuf.Any.pack(Person(name = "Ada").toProto()))
        val json = envelope.toJson(KrotoJsonOptions(typeRegistry = registry))
        Assertions.assertThat(Envelope.fromJson(json, KrotoJsonParseOptions(typeRegistry = registry))).isEqualTo(envelope)
    }
}

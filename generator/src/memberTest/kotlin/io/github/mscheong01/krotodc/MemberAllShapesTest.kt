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
import com.example.membertest.krotodc.AllShapes
import com.example.membertest.krotodc.Person
import com.example.membertest.krotodc.allshapes.toDataClass
import com.google.protobuf.ByteString
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

/** Runs in both memberTest (converterStyle=builderApply) and explicitMemberTest (converterStyle=explicit). */
class MemberAllShapesTest {

    private val time = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

    private val full = AllShapes(
        wrappedInt = 0,
        wrappedString = "w",
        wrappedLongs = listOf(1L, 0L),
        colors = listOf(Color.RED, Color.BLUE),
        colorById = mapOf(1 to Color.RED, 2 to Color.COLOR_UNSPECIFIED),
        times = mapOf("t" to time),
        moments = listOf(time, time.plusDays(1)),
        maybeAddress = Person.Address(city = "Maybe"),
        maybeCount = 0L,
        blob = ByteString.copyFromUtf8("blob"),
        tags = listOf("a", "b"),
        wait = Duration.ofSeconds(3, 5),
        `class` = "c",
        `in` = 7,
        choice = AllShapes.Choice.Hue(Color.COLOR_UNSPECIFIED),
        people = listOf(Person(name = "Ada", previous = listOf(Person.Address(city = "London")))),
        labels = mapOf("k" to "v"),
        `else` = mapOf("e" to Person.Address(city = "Else")),
        `for` = listOf(Person.Address(city = "For"), Person.Address()),
        chunks = listOf(ByteString.copyFromUtf8("c1"), ByteString.EMPTY),
        blobs = mapOf("b" to ByteString.copyFromUtf8("B")),
        waits = listOf(Duration.ofMillis(5))
    )

    private val choices = listOf(
        null,
        AllShapes.Choice.At(time),
        AllShapes.Choice.Hue(Color.BLUE),
        AllShapes.Choice.Place(Person.Address(city = "Place"))
    )

    @Test
    fun `every shape round trips through proto, bytes and json`() {
        val messages = listOf(full, AllShapes()) + choices.map { full.copy(choice = it) }
        for (message in messages) {
            Assertions.assertThat(message.toProto().toDataClass()).isEqualTo(message)
            Assertions.assertThat(AllShapes.parseFrom(message.toByteArray())).isEqualTo(message)
            Assertions.assertThat(AllShapes.fromJson(message.toJson())).isEqualTo(message)
        }
    }

    @Test
    fun `toProto sets every field as the java builder would`() {
        val proto = full.toProto()
        val expected = MemberTest.AllShapes.newBuilder()
            .setWrappedInt(com.google.protobuf.Int32Value.of(0))
            .setWrappedString(com.google.protobuf.StringValue.of("w"))
            .addWrappedLongs(com.google.protobuf.Int64Value.of(1L))
            .addWrappedLongs(com.google.protobuf.Int64Value.of(0L))
            .setNothing(com.google.protobuf.Empty.getDefaultInstance())
            .addAllColors(listOf(Color.RED, Color.BLUE))
            .putColorById(1, Color.RED)
            .putColorById(2, Color.COLOR_UNSPECIFIED)
            .putTimes("t", proto.timesMap.getValue("t"))
            .addAllMoments(proto.momentsList)
            .setMaybeAddress(MemberTest.Person.Address.newBuilder().setCity("Maybe"))
            .setMaybeCount(0L)
            .setBlob(ByteString.copyFromUtf8("blob"))
            .addAllTags(listOf("a", "b"))
            .setWait(com.google.protobuf.Duration.newBuilder().setSeconds(3).setNanos(5))
            .setClass_("c")
            .setIn(7)
            .setHue(Color.COLOR_UNSPECIFIED)
            .addPeople(Person(name = "Ada", previous = listOf(Person.Address(city = "London"))).toProto())
            .putLabels("k", com.google.protobuf.StringValue.of("v"))
            .putElse("e", MemberTest.Person.Address.newBuilder().setCity("Else").build())
            .addFor(MemberTest.Person.Address.newBuilder().setCity("For"))
            .addFor(MemberTest.Person.Address.getDefaultInstance())
            .addAllChunks(listOf(ByteString.copyFromUtf8("c1"), ByteString.EMPTY))
            .putBlobs("b", ByteString.copyFromUtf8("B"))
            .addWaits(com.google.protobuf.Duration.newBuilder().setNanos(5_000_000))
            .build()
        Assertions.assertThat(proto).isEqualTo(expected)
        Assertions.assertThat(full.toByteArray()).containsExactly(*expected.toByteArray())
        Assertions.assertThat(proto.momentsList.map { it.toLocalDateTime() }).containsExactly(time, time.plusDays(1))
    }

    @Test
    fun `presence is kept for wrappers, optional fields and oneofs`() {
        val empty = AllShapes().toProto()
        Assertions.assertThat(empty.hasWrappedInt()).isFalse
        Assertions.assertThat(empty.hasWrappedString()).isFalse
        Assertions.assertThat(empty.hasMaybeAddress()).isFalse
        Assertions.assertThat(empty.hasMaybeCount()).isFalse
        Assertions.assertThat(empty.choiceCase).isEqualTo(MemberTest.AllShapes.ChoiceCase.CHOICE_NOT_SET)

        val set = full.toProto()
        Assertions.assertThat(set.hasWrappedInt()).isTrue
        Assertions.assertThat(set.hasMaybeCount()).isTrue
        Assertions.assertThat(set.choiceCase).isEqualTo(MemberTest.AllShapes.ChoiceCase.HUE)

        val fromJava = MemberTest.AllShapes.newBuilder().setMaybeCount(0L).setHue(Color.RED).build().toDataClass()
        Assertions.assertThat(fromJava.wrappedInt).isNull()
        Assertions.assertThat(fromJava.wrappedString).isNull()
        Assertions.assertThat(fromJava.maybeAddress).isNull()
        Assertions.assertThat(fromJava.maybeCount).isEqualTo(0L)
        Assertions.assertThat(fromJava.choice).isEqualTo(AllShapes.Choice.Hue(Color.RED))
        Assertions.assertThat(fromJava.`for`).isEmpty()
        Assertions.assertThat(fromJava.`else`).isEmpty()
    }
}

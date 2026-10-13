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

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Message

/**
 * Implemented by generated data classes when the plugin runs with `converterLocation=member`, so code that only has a
 * [Class] or an instance can convert without reflection.
 */
interface KrotoDCMessage<P : Message> {
    fun toProto(): P

    /** Canonical protobuf JSON. With [includeDefaultValues], fields without presence are printed and whitespace is omitted. */
    fun toJson(includeDefaultValues: Boolean = false): String

    /** Protobuf JSON printed as [options] say, e.g. with proto field names or compact without defaults. */
    fun toJson(options: KrotoJsonOptions): String = KrotoJson.print(toProto(), options)

    /** Protobuf binary wire format, identical to `toProto().toByteArray()`. */
    fun toByteArray(): ByteArray = toProto().toByteArray()

    /** Protobuf binary wire format, identical to `toProto().toByteString()`. */
    fun toByteString(): ByteString = toProto().toByteString()
}

/** Implemented by the companion object of every [KrotoDCMessage] data class. */
interface KrotoDCMessageCompanion<D : KrotoDCMessage<P>, P : Message> {
    fun fromProto(proto: P): D

    /** Parses protobuf JSON, ignoring unknown fields. */
    fun fromJson(json: String): D

    /** Parses protobuf JSON as [options] say; [KrotoJsonParseOptions.STRICT] rejects unknown fields. */
    fun fromJson(json: String, options: KrotoJsonParseOptions): D =
        fromProtoUnchecked(KrotoJson.merge(json, newProtoBuilder(), options).build())

    /** Parses protobuf binary wire format. Throws [com.google.protobuf.InvalidProtocolBufferException] on malformed input. */
    fun parseFrom(bytes: ByteArray): D

    /** Parses protobuf binary wire format. Throws [com.google.protobuf.InvalidProtocolBufferException] on malformed input. */
    fun parseFrom(bytes: ByteString): D

    fun newProtoBuilder(): Message.Builder

    /** The message's protobuf descriptor. `descriptor.file` is the descriptor of the `.proto` file that declares it. */
    val descriptor: Descriptors.Descriptor

    /** [fromProto] for callers that only know the message type at runtime. */
    @Suppress("UNCHECKED_CAST")
    fun fromProtoUnchecked(proto: Message): D = fromProto(proto as P)
}

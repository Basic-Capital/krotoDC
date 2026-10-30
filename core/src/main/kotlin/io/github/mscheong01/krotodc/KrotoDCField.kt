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

/**
 * The protobuf type of a [KrotoDCField], i.e. its [com.google.protobuf.Descriptors.FieldDescriptor.JavaType]. For
 * repeated fields it is the element type; map fields and well-known types are [MESSAGE] even when the data class
 * property is a Kotlin type such as `Map`, `LocalDateTime` or `Int?`.
 */
enum class KrotoDCFieldKind { INT, LONG, FLOAT, DOUBLE, BOOL, STRING, BYTES, ENUM, MESSAGE }

/** How a [KrotoDCField]'s data class property holds its value. */
enum class KrotoDCCardinality {
    /**
     * A non-nullable singular property: a proto3 scalar or enum without `optional`, or a singular message field,
     * which krotoDC gives a non-null default instead of keeping presence.
     */
    IMPLICIT,

    /** A nullable property (`optional` fields and wrapper types such as `Int32Value`) or a oneof member. */
    OPTIONAL,

    /** A `repeated` field, held as a `List`. */
    REPEATED,

    /** A `map` field, held as a `Map`. */
    MAP
}

/**
 * One proto field of a generated data class [D] whose value has Kotlin type [V], with reflection-free accessors.
 * Generated with `fieldAccessors=true` as the properties of the data class's nested `Fields` object (`KrotoFields`
 * if the message has a nested type named `Fields`) and listed, in declaration order, by
 * [KrotoDCMessageCompanion.fields].
 *
 * [V] is the property type as krotoDC maps it, without nullability: `String`, `Int`, `Long`, `Boolean`, `Double`,
 * `Float`, `ByteString`, the protoc Java enum, the generated data class, `List<T>` for repeated fields, `Map<K, V>`
 * for map fields, and the mapped Kotlin type for well-known types (`LocalDateTime` for `Timestamp`,
 * `java.time.Duration` for `Duration`, `Int` for `Int32Value`, `Unit` for `Empty`, the protoc Java class for others
 * such as `Any`). For a oneof member it is the type of that case's value.
 */
class KrotoDCField<D : KrotoDCMessage<*>, V : Any>(
    /** The proto field name. */
    val name: String,
    /** The proto JSON name, [com.google.protobuf.Descriptors.FieldDescriptor.getJsonName]. */
    val jsonName: String,
    /**
     * The data class property name. For a oneof member it is the property of the case class, e.g. `email` in
     * `Contact.Email(email)`, whose holder is the data class property named after the oneof.
     */
    val propertyName: String,
    val number: Int,
    val kind: KrotoDCFieldKind,
    val cardinality: KrotoDCCardinality,
    /**
     * The companion of the generated data class for a [KrotoDCFieldKind.MESSAGE] field, or of its elements if
     * repeated. Null for other kinds, map fields and well-known types, which have no generated data class.
     */
    val messageCompanion: KrotoDCMessageCompanion<*, *>?,
    /** The proto name of the oneof this field belongs to; null for fields outside a oneof, including proto3 `optional`. */
    val oneofName: String?,
    private val getter: (D) -> V?,
    private val presence: (D) -> Boolean,
    private val setter: (D, V?) -> D
) {
    /** The field's value in [message]. Null if the property is null, or for a oneof member when another case (or none) is set. */
    fun get(message: D): V? = getter(message)

    /**
     * Whether [message] sets the field, following proto semantics as far as the data class can express them:
     * [KrotoDCCardinality.OPTIONAL] fields when non-null (oneof members when their case is selected),
     * [KrotoDCCardinality.IMPLICIT] fields when different from the default (scalars compare bits, so `-0.0` is set),
     * [KrotoDCCardinality.REPEATED] and [KrotoDCCardinality.MAP] fields when non-empty.
     */
    fun has(message: D): Boolean = presence(message)

    /**
     * A copy of [message] with the field set to [value]. A null [value] clears it: optional properties become null,
     * implicit ones take the default the constructor uses, collections become empty, and a oneof member clears the
     * oneof only if that member's case is selected.
     */
    fun set(message: D, value: V?): D = setter(message, value)

    override fun toString(): String = "KrotoDCField($name = $number)"
}

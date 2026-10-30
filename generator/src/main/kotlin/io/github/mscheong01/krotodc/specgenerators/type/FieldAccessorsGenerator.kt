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
package io.github.mscheong01.krotodc.specgenerators.type

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.mscheong01.krotodc.KrotoDCCardinality
import io.github.mscheong01.krotodc.KrotoDCField
import io.github.mscheong01.krotodc.KrotoDCFieldKind
import io.github.mscheong01.krotodc.KrotoDCOptions
import io.github.mscheong01.krotodc.import.CodeWithImports
import io.github.mscheong01.krotodc.import.Import
import io.github.mscheong01.krotodc.specgenerators.file.DataClassGenerator
import io.github.mscheong01.krotodc.util.capitalize
import io.github.mscheong01.krotodc.util.fieldNameToJsonName
import io.github.mscheong01.krotodc.util.isKrotoDCOptional
import io.github.mscheong01.krotodc.util.isPredefinedType
import io.github.mscheong01.krotodc.util.krotoDCPackage
import io.github.mscheong01.krotodc.util.krotoDCTypeName
import io.github.mscheong01.krotodc.util.protobufJavaTypeName
import io.github.mscheong01.krotodc.util.simpleNames

/**
 * `fieldAccessors=true`: a nested `Fields` object on the data class with one `@JvmField` [KrotoDCField] per proto
 * field, plus a list of them that the companion's `fields` override returns.
 *
 * The object is named `Fields` unless the data class already has a nested type of that name (a nested message, or a
 * oneof named `fields`) or is itself named `Fields`; then it is `KrotoFields`, then `KrotoFields2` and so on.
 *
 * Class initialization: the companion's `fields` is a getter, so nothing touches the `Fields` object until it is first
 * read, and initializing `Fields` only reads the companions of other messages (for `messageCompanion`), whose
 * initialization never touches a `Fields` object. So mutually recursive messages cannot form an initialization cycle.
 *
 * The accessor lambdas are written with explicit parameter types and explicit constructor type arguments so the
 * compiler has no inference to do, in the spirit of `converterStyle=explicit`.
 */
class FieldAccessorsGenerator(private val dataClassGenerator: DataClassGenerator) {

    fun addFieldAccessors(
        messageDescriptor: Descriptor,
        dataClassBuilder: TypeSpec.Builder,
        companionBuilder: TypeSpec.Builder,
        imports: MutableSet<Import>
    ) {
        if (messageDescriptor.fields.isEmpty()) {
            return
        }
        val dataClassType = messageDescriptor.krotoDCTypeName
        val fieldTypeName = KrotoDCField::class.asClassName()
        val listType = LIST.parameterizedBy(fieldTypeName.parameterizedBy(dataClassType, STAR))

        val takenTypeNames = dataClassBuilder.typeSpecs.mapNotNull { it.name }.toSet() + dataClassType.simpleNames
        val objectName = fieldsObjectName(takenTypeNames)
        val objectType = dataClassType.nestedClass(objectName)
        val objectBuilder = TypeSpec.objectBuilder(objectName)
            .addKdoc("Typed accessors for the fields of [%T], in proto declaration order.", dataClassType)

        val propertyNames = messageDescriptor.fields.map { it.jsonName }
        for (field in messageDescriptor.fields) {
            val (valueType, initializer) = fieldInitializer(messageDescriptor, field, imports)
            objectBuilder.addProperty(
                PropertySpec.builder(field.jsonName, fieldTypeName.parameterizedBy(dataClassType, valueType))
                    .addAnnotation(JvmField::class)
                    .initializer(initializer)
                    .build()
            )
        }
        val listName = generateSequence("all") { it + "_" }.first { it !in propertyNames }
        val listInitializer = CodeBlock.builder()
            .add("listOf<%T>(\n⇥", listType.typeArguments.single())
            .apply {
                propertyNames.forEachIndexed { index, name ->
                    add("%N%L\n", name, if (index < propertyNames.size - 1) "," else "")
                }
            }
            .add("⇤)")
            .build()
        objectBuilder.addProperty(
            PropertySpec.builder(listName, listType)
                .addKdoc("Every field above, in order. Declared last so the fields are initialized first.")
                .addAnnotation(JvmField::class)
                .initializer(listInitializer)
                .build()
        )
        if (messageDescriptor.fields.any { it.options.deprecated }) {
            objectBuilder.addAnnotation(
                AnnotationSpec.builder(Suppress::class).addMember("%S", "DEPRECATION").build()
            )
        }
        dataClassBuilder.addType(objectBuilder.build())

        companionBuilder.addProperty(
            PropertySpec.builder("fields", listType)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %T.%N", objectType, listName)
                        .build()
                )
                .build()
        )
    }

    private fun fieldsObjectName(takenTypeNames: Set<String>): String =
        (sequenceOf("Fields", "KrotoFields") + generateSequence(2) { it + 1 }.map { "KrotoFields$it" })
            .first { it !in takenTypeNames }

    /** The element type `V` of the field's [KrotoDCField] and the expression constructing it. */
    private fun fieldInitializer(
        messageDescriptor: Descriptor,
        field: FieldDescriptor,
        imports: MutableSet<Import>
    ): Pair<TypeName, CodeBlock> {
        val dataClassType = messageDescriptor.krotoDCTypeName
        val (propertyType, default) = dataClassGenerator.mapProtoTypeToKotlinTypeAndDefaultValue(field)
        val valueType = propertyType.copy(nullable = false)
        val oneOf = field.realContainingOneof
        val cardinality = when {
            field.isMapField -> KrotoDCCardinality.MAP
            field.isRepeated -> KrotoDCCardinality.REPEATED
            oneOf != null || field.isKrotoDCOptional -> KrotoDCCardinality.OPTIONAL
            else -> KrotoDCCardinality.IMPLICIT
        }
        val messageCompanion = if (
            field.javaType == FieldDescriptor.JavaType.MESSAGE &&
            !field.isMapField &&
            !field.messageType.isPredefinedType()
        ) {
            CodeBlock.of("%T.Companion", field.messageType.krotoDCTypeName)
        } else {
            CodeBlock.of("null")
        }

        val property = field.jsonName
        val accessors = if (oneOf != null) {
            val holder = fieldNameToJsonName(oneOf.name)
            val caseType = ClassName(
                oneOf.file.krotoDCPackage,
                messageDescriptor.simpleNames + holder.capitalize() + field.jsonName.capitalize()
            )
            listOf(
                CodeBlock.of("{·m:·%T·->·(m.%N·as?·%T)?.%N·}", dataClassType, holder, caseType, property),
                CodeBlock.of("{·m:·%T·->·m.%N·is·%T·}", dataClassType, holder, caseType),
                CodeBlock.of(
                    "{·m:·%T,·v:·%T·->·if·(v·!=·null)·m.copy(%N·=·%T(v))·else·if·(m.%N·is·%T)·m.copy(%N·=·null)·else·m·}",
                    dataClassType,
                    valueType.copy(nullable = true),
                    holder,
                    caseType,
                    holder,
                    caseType,
                    holder
                )
            )
        } else {
            val (presence, reset) = when (cardinality) {
                KrotoDCCardinality.OPTIONAL -> CodeBlock.of("m.%N·!=·null", property) to null
                KrotoDCCardinality.REPEATED -> CodeBlock.of("m.%N.isNotEmpty()", property) to
                    CodeBlock.of("emptyList<%T>()", (valueType as ParameterizedTypeName).typeArguments.single())
                KrotoDCCardinality.MAP -> CodeBlock.of("m.%N.isNotEmpty()", property) to
                    CodeBlock.of("emptyMap<%T,·%T>()", *(valueType as ParameterizedTypeName).typeArguments.toTypedArray())
                KrotoDCCardinality.IMPLICIT -> {
                    val implicitDefault = implicitDefault(field, default)
                    imports.addAll(implicitDefault.imports)
                    implicitPresence(field, property, implicitDefault.code) to implicitDefault.code
                }
            }
            val setValue = if (reset == null) CodeBlock.of("v") else CodeBlock.of("v·?:·%L", reset)
            listOf(
                CodeBlock.of("{·m:·%T·->·m.%N·}", dataClassType, property),
                CodeBlock.of("{·m:·%T·->·%L·}", dataClassType, presence),
                CodeBlock.of(
                    "{·m:·%T,·v:·%T·->·m.copy(%N·=·%L)·}",
                    dataClassType,
                    valueType.copy(nullable = true),
                    property,
                    setValue
                )
            )
        }

        val initializer = CodeBlock.builder()
            .add("%T<%T,·%T>(\n⇥", KrotoDCField::class, dataClassType, valueType)
            .add("%S,\n%S,\n%S,\n%L,\n", field.name, field.jsonName, property, field.number)
            .add("%T.%L,\n", KrotoDCFieldKind::class, fieldKind(field).name)
            .add("%T.%L,\n", KrotoDCCardinality::class, cardinality.name)
            .add("%L,\n", messageCompanion)
            .add(if (oneOf != null) CodeBlock.of("%S,\n", oneOf.name) else CodeBlock.of("null,\n"))
            .add("%L,\n%L,\n%L\n", *accessors.toTypedArray())
            .add("⇤)")
            .build()
        return valueType to initializer
    }

    /**
     * The default an implicit property is reset to: the constructor's, except that a generated message without
     * `allFieldDefaults` may have no no-arg constructor, so it is converted from the proto default instance instead.
     */
    private fun implicitDefault(field: FieldDescriptor, default: CodeWithImports): CodeWithImports =
        if (
            field.javaType == FieldDescriptor.JavaType.MESSAGE &&
            !field.messageType.isPredefinedType() &&
            !KrotoDCOptions.allFieldDefaults
        ) {
            CodeWithImports.of(
                CodeBlock.of(
                    "%T.fromProto(%T.getDefaultInstance())",
                    field.messageType.krotoDCTypeName,
                    field.messageType.protobufJavaTypeName
                )
            )
        } else {
            default
        }

    /** Whether an implicit property differs from its default, comparing scalars as protobuf-java's `has` checks do. */
    private fun implicitPresence(field: FieldDescriptor, property: String, default: CodeBlock): CodeBlock =
        when (field.javaType!!) {
            FieldDescriptor.JavaType.INT -> CodeBlock.of("m.%N·!=·0", property)
            FieldDescriptor.JavaType.LONG -> CodeBlock.of("m.%N·!=·0L", property)
            FieldDescriptor.JavaType.FLOAT -> CodeBlock.of("m.%N.toRawBits()·!=·0", property)
            FieldDescriptor.JavaType.DOUBLE -> CodeBlock.of("m.%N.toRawBits()·!=·0L", property)
            FieldDescriptor.JavaType.BOOLEAN -> CodeBlock.of("m.%N", property)
            FieldDescriptor.JavaType.STRING -> CodeBlock.of("m.%N.isNotEmpty()", property)
            FieldDescriptor.JavaType.BYTE_STRING -> CodeBlock.of("!m.%N.isEmpty()", property)
            FieldDescriptor.JavaType.ENUM,
            FieldDescriptor.JavaType.MESSAGE -> CodeBlock.of("m.%N·!=·%L", property, default)
        }

    private fun fieldKind(field: FieldDescriptor): KrotoDCFieldKind = when (field.javaType!!) {
        FieldDescriptor.JavaType.INT -> KrotoDCFieldKind.INT
        FieldDescriptor.JavaType.LONG -> KrotoDCFieldKind.LONG
        FieldDescriptor.JavaType.FLOAT -> KrotoDCFieldKind.FLOAT
        FieldDescriptor.JavaType.DOUBLE -> KrotoDCFieldKind.DOUBLE
        FieldDescriptor.JavaType.BOOLEAN -> KrotoDCFieldKind.BOOL
        FieldDescriptor.JavaType.STRING -> KrotoDCFieldKind.STRING
        FieldDescriptor.JavaType.BYTE_STRING -> KrotoDCFieldKind.BYTES
        FieldDescriptor.JavaType.ENUM -> KrotoDCFieldKind.ENUM
        FieldDescriptor.JavaType.MESSAGE -> KrotoDCFieldKind.MESSAGE
    }
}

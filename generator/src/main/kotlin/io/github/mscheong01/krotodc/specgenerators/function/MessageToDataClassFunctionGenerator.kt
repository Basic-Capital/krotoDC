// Copyright 2023 Minsoo Cheong
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
package io.github.mscheong01.krotodc.specgenerators.function

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.mscheong01.krotodc.KrotoDCConverter
import io.github.mscheong01.krotodc.KrotoDCOptions
import io.github.mscheong01.krotodc.import.CodeWithImports
import io.github.mscheong01.krotodc.import.FunSpecsWithImports
import io.github.mscheong01.krotodc.import.Import
import io.github.mscheong01.krotodc.predefinedtypes.HandledPreDefinedType
import io.github.mscheong01.krotodc.specgenerators.FunSpecGenerator
import io.github.mscheong01.krotodc.specgenerators.file.DataClassGenerator
import io.github.mscheong01.krotodc.template.TransformTemplateWithImports
import io.github.mscheong01.krotodc.util.MAP_ENTRY_VALUE_FIELD_NUMBER
import io.github.mscheong01.krotodc.util.capitalize
import io.github.mscheong01.krotodc.util.endControlFlowWithComma
import io.github.mscheong01.krotodc.util.escapeIfNecessary
import io.github.mscheong01.krotodc.util.fieldNameToJsonName
import io.github.mscheong01.krotodc.util.isHandledPreDefinedType
import io.github.mscheong01.krotodc.util.isKrotoDCOptional
import io.github.mscheong01.krotodc.util.isPredefinedType
import io.github.mscheong01.krotodc.util.javaFieldName
import io.github.mscheong01.krotodc.util.krotoDCPackage
import io.github.mscheong01.krotodc.util.krotoDCTypeName
import io.github.mscheong01.krotodc.util.protobufJavaTypeName
import io.github.mscheong01.krotodc.util.simpleNames
import io.github.mscheong01.krotodc.util.toDataClassImport

class MessageToDataClassFunctionGenerator : FunSpecGenerator<Descriptor> {
    override fun generate(descriptor: Descriptor): FunSpecsWithImports {
        val imports = mutableSetOf<Import>()
        val generatedType = descriptor.krotoDCTypeName
        val protoType = descriptor.protobufJavaTypeName
        val functionBuilder = FunSpec.builder("toDataClass")
            .receiver(protoType)
            .returns(generatedType)
        if (KrotoDCOptions.explicitConverters) {
            addExplicitBody(descriptor, functionBuilder, imports)
            return finish(functionBuilder, generatedType, protoType, imports)
        }

        functionBuilder.addCode("return %T(", generatedType)

        for (oneOf in descriptor.realOneofs) {
            val oneOfJsonName = fieldNameToJsonName(oneOf.name)
            functionBuilder.beginControlFlow(
                "%L = when (%LCase)",
                oneOfJsonName.escapeIfNecessary(),
                oneOfJsonName
            )
            for (field in oneOf.fields) {
                val dataClassFieldName = field.jsonName
                val protoFieldName = field.javaFieldName
                val (template, downStreamImports) = transformCodeTemplate(field)
                val oneOfDataClassName = ClassName(
                    oneOf.file.krotoDCPackage,
                    *descriptor.simpleNames.toMutableList().apply {
                        add(oneOfJsonName.capitalize())
                        add(field.jsonName.capitalize())
                    }.toTypedArray()
                )
                functionBuilder.addStatement(
                    "%L.%LCase.%L -> %L( %L = %L )".trimIndent(),
                    protoType,
                    oneOfJsonName.capitalize(),
                    field.name.uppercase(),
                    oneOfDataClassName.canonicalName,
                    dataClassFieldName.escapeIfNecessary(),
                    template.safeCall(protoFieldName.escapeIfNecessary())
                )
                imports.addAll(downStreamImports)
            }
            functionBuilder.addStatement("%L.%LCase.%L -> null", protoType, oneOfJsonName.capitalize(), "${oneOf.name.replace("_", "").uppercase()}_NOT_SET".uppercase())
            functionBuilder.addStatement("null -> null")
            functionBuilder.endControlFlowWithComma()
        }

        for (field in descriptor.fields) {
            if (field.name in descriptor.realOneofs.map { it.fields }.flatten().map { it.name }.toSet()) {
                continue
            }

            val dataClassFieldName = field.jsonName
            val protoFieldName = field.javaFieldName
            val optional = field.isKrotoDCOptional
            functionBuilder.addCode("%L = ", dataClassFieldName.escapeIfNecessary())
            if (optional) {
                functionBuilder.beginControlFlow("if (has${protoFieldName.capitalize()}())")
            }

            val codeWithImports = if (field.isMapField) {
                val valueField = field.messageType.findFieldByNumber(MAP_ENTRY_VALUE_FIELD_NUMBER)
                val (template, downStreamImports) = transformCodeTemplate(valueField)
                val mapCodeBlock = if (template.value == "%L") {
                    CodeBlock.of("%LMap", protoFieldName)
                } else {
                    CodeBlock.of(
                        "%LMap.mapValues { %L }",
                        protoFieldName,
                        template.safeCall("it.value")
                    )
                }
                CodeWithImports.of(mapCodeBlock, downStreamImports)
            } else if (field.isRepeated) {
                val (template, downStreamImports) = transformCodeTemplate(field)
                val repeatedCodeBlock = if (template.value == "%L") {
                    CodeBlock.of("%LList", protoFieldName)
                } else {
                    CodeBlock.of("%LList.map { %L }", protoFieldName, template.safeCall("it"))
                }
                CodeWithImports.of(repeatedCodeBlock, downStreamImports)
            } else {
                val (template, downStreamImports) = transformCodeTemplate(field)
                CodeWithImports.of(
                    template.safeCall(protoFieldName.escapeIfNecessary()),
                    downStreamImports
                )
            }

            imports.addAll(codeWithImports.imports)
            functionBuilder.addStatement("%L", codeWithImports.code)
            if (optional) {
                functionBuilder.nextControlFlow("else")
                functionBuilder.addStatement("null")
                functionBuilder.endControlFlowWithComma()
            } else {
                functionBuilder.addCode(", ")
            }
        }
        functionBuilder.addCode(")")
        return finish(functionBuilder, generatedType, protoType, imports)
    }

    private fun finish(
        functionBuilder: FunSpec.Builder,
        generatedType: ClassName,
        protoType: ClassName,
        imports: Set<Import>
    ): FunSpecsWithImports {
        functionBuilder.addKdoc(
            "Converts [%T] to [%T]",
            protoType,
            generatedType
        )
        if (KrotoDCOptions.converterAnnotations) {
            functionBuilder.addAnnotation(
                AnnotationSpec.builder(KrotoDCConverter::class)
                    .addMember("from = %T::class", protoType)
                    .addMember("to = %T::class", generatedType)
                    .build()
            )
        }

        return FunSpecsWithImports(
            listOf(functionBuilder.build()),
            imports
        )
    }

    /**
     * `converterStyle=explicit`: converted collections and oneofs go into explicitly typed locals filled by plain
     * loops, every getter has an explicit `this.` receiver, and the constructor call takes named arguments. Element,
     * key and value types are the data class property types. Untransformed collections are passed through as the proto's
     * lists and maps, as in the `builderApply` style.
     */
    private fun addExplicitBody(descriptor: Descriptor, functionBuilder: FunSpec.Builder, imports: MutableSet<Import>) {
        val generatedType = descriptor.krotoDCTypeName
        val protoType = descriptor.protobufJavaTypeName
        val dataClassGenerator = DataClassGenerator()
        val arguments = mutableListOf<CodeBlock>()
        val oneOfFieldNames = descriptor.realOneofs.flatMap { it.fields }.map { it.name }.toSet()

        for (oneOf in descriptor.realOneofs) {
            val oneOfJsonName = fieldNameToJsonName(oneOf.name)
            val interfaceClassName = ClassName(
                oneOf.file.krotoDCPackage,
                *descriptor.simpleNames.toMutableList().apply { add(oneOfJsonName.capitalize()) }.toTypedArray()
            )
            val caseClassName = protoType.nestedClass("${oneOfJsonName.capitalize()}Case")
            val local = "${oneOfJsonName}Converted"
            functionBuilder.beginControlFlow(
                "val·%L:·%T·=·when·(this.%LCase)",
                local,
                interfaceClassName.copy(nullable = true),
                oneOfJsonName
            )
            for (field in oneOf.fields) {
                val (template, downStreamImports) = transformCodeTemplate(field)
                imports.addAll(downStreamImports)
                functionBuilder.addStatement(
                    "%T.%L·->·%T(%L·=·%L)",
                    caseClassName,
                    field.name.uppercase(),
                    interfaceClassName.nestedClass(field.jsonName.capitalize()),
                    field.jsonName.escapeIfNecessary(),
                    template.safeCall("this.${field.javaFieldName.escapeIfNecessary()}")
                )
            }
            functionBuilder.addStatement(
                "%T.%L·->·null",
                caseClassName,
                "${oneOf.name.replace("_", "").uppercase()}_NOT_SET"
            )
            functionBuilder.addStatement("null·->·null")
            functionBuilder.endControlFlow()
            arguments.add(CodeBlock.of("%L·=·%L", oneOfJsonName.escapeIfNecessary(), local))
        }

        for (field in descriptor.fields) {
            if (field.name in oneOfFieldNames) {
                continue
            }
            val argumentName = field.jsonName.escapeIfNecessary()
            val protoFieldName = field.javaFieldName
            val local = "${field.jsonName}Converted"
            val propertyType = dataClassGenerator.mapProtoTypeToKotlinTypeAndDefaultValue(field).first
            if (field.isMapField) {
                val valueField = field.messageType.findFieldByNumber(MAP_ENTRY_VALUE_FIELD_NUMBER)
                val (template, downStreamImports) = transformCodeTemplate(valueField)
                imports.addAll(downStreamImports)
                if (template.value == "%L") {
                    arguments.add(CodeBlock.of("%L·=·this.%LMap", argumentName, protoFieldName))
                    continue
                }
                val typeArguments = (propertyType as ParameterizedTypeName).typeArguments
                val localType = LINKED_HASH_MAP.parameterizedBy(typeArguments[0], typeArguments[1])
                functionBuilder.addStatement("val·%L:·%T·=·%T(this.%LCount)", local, localType, localType, protoFieldName)
                functionBuilder.beginControlFlow("for·(entry·in·this.%LMap.entries)", protoFieldName)
                functionBuilder.addStatement("%L.put(entry.key,·%L)", local, template.safeCall("entry.value"))
                functionBuilder.endControlFlow()
                arguments.add(CodeBlock.of("%L·=·%L", argumentName, local))
            } else if (field.isRepeated) {
                val (template, downStreamImports) = transformCodeTemplate(field)
                imports.addAll(downStreamImports)
                if (template.value == "%L") {
                    arguments.add(CodeBlock.of("%L·=·this.%LList", argumentName, protoFieldName))
                    continue
                }
                val elementType = (propertyType as ParameterizedTypeName).typeArguments[0]
                val localType = ARRAY_LIST.parameterizedBy(elementType)
                functionBuilder.addStatement("val·%L:·%T·=·%T(this.%LCount)", local, localType, localType, protoFieldName)
                functionBuilder.beginControlFlow("for·(element·in·this.%LList)", protoFieldName)
                functionBuilder.addStatement("%L.add(%L)", local, template.safeCall("element"))
                functionBuilder.endControlFlow()
                arguments.add(CodeBlock.of("%L·=·%L", argumentName, local))
            } else {
                val (template, downStreamImports) = transformCodeTemplate(field)
                imports.addAll(downStreamImports)
                val value = template.safeCall("this.${protoFieldName.escapeIfNecessary()}")
                if (field.isKrotoDCOptional) {
                    arguments.add(
                        CodeBlock.of(
                            "%L·=·if·(this.has%L())·%L·else·null",
                            argumentName,
                            protoFieldName.capitalize(),
                            value
                        )
                    )
                } else {
                    arguments.add(CodeBlock.of("%L·=·%L", argumentName, value))
                }
            }
        }

        if (arguments.isEmpty()) {
            functionBuilder.addStatement("return·%T()", generatedType)
            return
        }
        functionBuilder.addCode("return·%T(\n⇥", generatedType)
        for (argument in arguments) {
            functionBuilder.addCode("%L,\n", argument)
        }
        functionBuilder.addCode("⇤)\n")
    }

    // code template that could be used like CodeBlock.of(transformCodeTemplate, fieldName)
    fun transformCodeTemplate(field: FieldDescriptor): TransformTemplateWithImports {
        return when (field.type) {
            FieldDescriptor.Type.MESSAGE -> {
                messageTypeTransformCodeTemplate(field.messageType)
            }
            else -> {
                TransformTemplateWithImports.of("%L")
            }
        }
    }

    companion object {
        private val ARRAY_LIST = ClassName("kotlin.collections", "ArrayList")
        private val LINKED_HASH_MAP = ClassName("kotlin.collections", "LinkedHashMap")

        fun messageTypeTransformCodeTemplate(descriptor: Descriptor): TransformTemplateWithImports {
            return when {
                descriptor.isPredefinedType() -> {
                    preDefinedTypeTransformCodeTemplate(descriptor)
                }
                else -> TransformTemplateWithImports.of("%L.toDataClass()", setOf(descriptor.toDataClassImport))
            }
        }

        private fun preDefinedTypeTransformCodeTemplate(
            descriptor: Descriptor
        ): TransformTemplateWithImports {
            return if (descriptor.isHandledPreDefinedType()) {
                HandledPreDefinedType.valueOfByDescriptor(descriptor).toDataClassTemplate
            } else {
                TransformTemplateWithImports.of("%L")
            }
        }
    }
}

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
package io.github.mscheong01.krotodc.specgenerators.file

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FieldDescriptor
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.mscheong01.krotodc.KrotoDCExtension
import io.github.mscheong01.krotodc.KrotoDCOptions
import io.github.mscheong01.krotodc.specgenerators.FileSpecGenerator
import io.github.mscheong01.krotodc.util.fieldNameToJsonName
import io.github.mscheong01.krotodc.util.javaOuterClassName
import io.github.mscheong01.krotodc.util.krotoDCPackage
import io.github.mscheong01.krotodc.util.protobufJavaTypeName

/**
 * With `extensionAccessors=true`, generates `object <OuterClassName>Kroto` for each `.proto` file that declares
 * top-level extensions, with the file's descriptor and a [KrotoDCExtension] per scalar or enum extension. Callers then
 * read custom options without importing protoc's outer class. Message-typed extensions aren't exposed: their values
 * would be protoc Java messages.
 */
class ExtensionGenerator : FileSpecGenerator {
    override fun generate(fileDescriptor: Descriptors.FileDescriptor): List<FileSpec> {
        if (!KrotoDCOptions.extensionAccessors || fileDescriptor.extensions.isEmpty()) {
            return emptyList()
        }
        val outerClass = ClassName(fileDescriptor.javaPackageForOuterClass, fileDescriptor.javaOuterClassName)
        val objectName = fileDescriptor.javaOuterClassName + "Kroto"
        val objectBuilder = TypeSpec.objectBuilder(objectName)
            .addKdoc("Descriptor and custom options of `%L`.", fileDescriptor.name)
            .addProperty(
                PropertySpec.builder("descriptor", Descriptors.FileDescriptor::class)
                    .addAnnotation(JvmStatic::class)
                    .getter(FunSpec.getterBuilder().addStatement("return %T.getDescriptor()", outerClass).build())
                    .build()
            )
        for (extension in fileDescriptor.extensions) {
            val valueType = extension.kotlinValueType() ?: continue
            val name = fieldNameToJsonName(extension.name)
            val type = KrotoDCExtension::class.asClassName()
                .parameterizedBy(extension.containingType.protobufJavaTypeName, valueType)
            objectBuilder.addProperty(
                PropertySpec.builder(name, type)
                    .addAnnotation(JvmField::class)
                    .addKdoc("`(%L)` on `%L`.", extension.fullName, extension.containingType.fullName)
                    .initializer("%T(%T.%N)", KrotoDCExtension::class, outerClass, name)
                    .build()
            )
        }
        return listOf(
            FileSpec.builder(fileDescriptor.krotoDCPackage, "$objectName.kt")
                .addType(objectBuilder.build())
                .build()
        )
    }

    private val Descriptors.FileDescriptor.javaPackageForOuterClass: String
        get() = options.javaPackage.ifEmpty { `package` }

    private fun FieldDescriptor.kotlinValueType(): TypeName? {
        val elementType: TypeName = when (javaType) {
            FieldDescriptor.JavaType.INT -> INT
            FieldDescriptor.JavaType.LONG -> LONG
            FieldDescriptor.JavaType.FLOAT -> FLOAT
            FieldDescriptor.JavaType.DOUBLE -> DOUBLE
            FieldDescriptor.JavaType.BOOLEAN -> BOOLEAN
            FieldDescriptor.JavaType.STRING -> STRING
            FieldDescriptor.JavaType.BYTE_STRING -> ByteString::class.asClassName()
            FieldDescriptor.JavaType.ENUM -> enumType.protobufJavaTypeName
            FieldDescriptor.JavaType.MESSAGE, null -> return null
        }
        return if (isRepeated) LIST.parameterizedBy(elementType) else elementType
    }
}

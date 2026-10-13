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
package io.github.mscheong01.krotodc.specgenerators.file

import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.ServiceDescriptor
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.mscheong01.krotodc.KrotoDCOptions
import io.github.mscheong01.krotodc.specgenerators.FileSpecGenerator
import io.github.mscheong01.krotodc.specgenerators.TypeSpecGenerator
import io.github.mscheong01.krotodc.specgenerators.type.ClientStubGenerator
import io.github.mscheong01.krotodc.specgenerators.type.ServiceImplBaseGenerator
import io.github.mscheong01.krotodc.util.addAllImports
import io.github.mscheong01.krotodc.util.decapitalize
import io.github.mscheong01.krotodc.util.descriptorCode
import io.github.mscheong01.krotodc.util.grpcClass
import io.github.mscheong01.krotodc.util.krotoDCPackage
import io.github.mscheong01.krotodc.util.toUpperSnakeCase

class GrpcKrotoGenerator : FileSpecGenerator {
    override fun generate(fileDescriptor: Descriptors.FileDescriptor): List<FileSpec> {
        val fileSpecs = mutableListOf<FileSpec>()
        fileDescriptor.services.forEach { service ->
            val generators: List<TypeSpecGenerator<ServiceDescriptor>> = listOf(
                ServiceImplBaseGenerator(),
                ClientStubGenerator()
            )
            val results = generators.map {
                it.generate(service)
            }
            val krotoGrpcClassName = service.name + "GrpcKroto"
            fileSpecs.add(
                FileSpec.builder(fileDescriptor.krotoDCPackage, krotoGrpcClassName + ".kt")
                    .addType(
                        TypeSpec.objectBuilder(krotoGrpcClassName)
                            .apply {
                                if (KrotoDCOptions.memberConverters) {
                                    addServiceDescriptorMembers(this, service)
                                }
                                results.map {
                                    it.typeSpecs
                                }.flatten().forEach {
                                    addType(it)
                                }
                            }.build()
                    ).apply {
                        if (
                            service.methods.any {
                                it.isServerStreaming || it.isClientStreaming
                            }
                        ) {
                            addImport("kotlinx.coroutines.flow", "map")
                        }
                        addAllImports(
                            results.map {
                                it.imports
                            }.flatten().toSet()
                        )
                    }
                    .build()
            )
        }

        return fileSpecs
    }

    /**
     * Adds `SERVICE_NAME`, `serviceDescriptor`, a `<method>Method` descriptor per method and `FullMethodNames`
     * constants, so callers never import grpc-java's `<Service>Grpc` class. The descriptors still come from that class.
     * Method descriptors are typed `MethodDescriptor<*, *>`: their type arguments are protoc Java messages, which
     * callers shouldn't depend on.
     */
    private fun addServiceDescriptorMembers(objectBuilder: TypeSpec.Builder, service: ServiceDescriptor) {
        objectBuilder.addProperty(
            PropertySpec.builder("SERVICE_NAME", STRING, KModifier.CONST)
                .initializer("%S", service.fullName)
                .addKdoc("Full name of the `%L` service, as in grpc-java's `%L.SERVICE_NAME`.", service.name, service.grpcClass.simpleName)
                .build()
        )
        objectBuilder.addProperty(
            PropertySpec.builder("serviceDescriptor", io.grpc.ServiceDescriptor::class)
                .addAnnotation(JvmStatic::class)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %M()", service.grpcClass.member("getServiceDescriptor"))
                        .build()
                )
                .build()
        )
        val methodDescriptorType = io.grpc.MethodDescriptor::class.asClassName().parameterizedBy(STAR, STAR)
        val fullMethodNames = TypeSpec.objectBuilder("FullMethodNames")
            .addKdoc("`MethodDescriptor.fullMethodName` of each method, usable in `when` branches and annotations.")
        val constantNames = mutableMapOf<String, String>()
        for (method in service.methods) {
            objectBuilder.addProperty(
                PropertySpec.builder(method.name.decapitalize() + "Method", methodDescriptorType)
                    .addAnnotation(JvmStatic::class)
                    .getter(FunSpec.getterBuilder().addStatement("return %L", method.descriptorCode).build())
                    .build()
            )
            val constantName = method.name.toUpperSnakeCase()
            constantNames.put(constantName, method.name)?.let { other ->
                throw IllegalStateException(
                    "Methods ${service.fullName}.$other and ${method.name} both map to FullMethodNames.$constantName"
                )
            }
            fullMethodNames.addProperty(
                PropertySpec.builder(constantName, STRING, KModifier.CONST)
                    .initializer("%S", "${service.fullName}/${method.name}")
                    .build()
            )
        }
        objectBuilder.addType(fullMethodNames.build())
    }
}

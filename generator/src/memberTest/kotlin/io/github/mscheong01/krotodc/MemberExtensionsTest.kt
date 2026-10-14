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

import com.example.membertest.options.MemberOptions
import com.example.membertest.options.MemberOptions.Access
import com.example.membertest.options.krotodc.Account
import com.example.membertest.options.krotodc.AccountServiceGrpcKroto
import com.example.membertest.options.krotodc.MemberOptionsKroto
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import io.grpc.protobuf.ProtoMethodDescriptorSupplier
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MemberExtensionsTest {

    private fun methodOptions(name: String): DescriptorProtos.MethodOptions =
        (AccountServiceGrpcKroto.serviceDescriptor.methods.single { it.bareMethodName == name }.schemaDescriptor as ProtoMethodDescriptorSupplier)
            .methodDescriptor.options

    @Test
    fun `file object exposes the file descriptor`() {
        Assertions.assertThat(MemberOptionsKroto.descriptor).isSameAs(MemberOptions.getDescriptor())
        Assertions.assertThat(Account.descriptor.file).isSameAs(MemberOptionsKroto.descriptor)
    }

    @Test
    fun `extension descriptors are the declared extension fields`() {
        Assertions.assertThat(MemberOptionsKroto.access.descriptor).isSameAs(MemberOptions.access.descriptor)
        Assertions.assertThat(MemberOptionsKroto.access.descriptor.number).isEqualTo(51001)
        Assertions.assertThat(MemberOptionsKroto.fieldLabels.descriptor.fullName)
            .isEqualTo("com.example.membertest.options.field_labels")
    }

    @Test
    fun `method options read through the extension`() {
        val getAccount = methodOptions("GetAccount")
        Assertions.assertThat(MemberOptionsKroto.access.get(getAccount)).isEqualTo(Access.READ)
        Assertions.assertThat(MemberOptionsKroto.methodNote.has(getAccount)).isTrue()
        Assertions.assertThat(MemberOptionsKroto.methodNote.get(getAccount)).isEqualTo("reads an account")
    }

    @Test
    fun `unset options read as their defaults`() {
        val putAccount = methodOptions("PutAccount")
        Assertions.assertThat(MemberOptionsKroto.access.has(putAccount)).isFalse()
        Assertions.assertThat(MemberOptionsKroto.access.get(putAccount)).isEqualTo(Access.ACCESS_UNSPECIFIED)
        Assertions.assertThat(MemberOptionsKroto.methodNote.get(putAccount)).isEmpty()
    }

    @Test
    fun `field and enum value options read through the extension`() {
        val secret = Account.descriptor.findFieldByName("secret").options
        Assertions.assertThat(MemberOptionsKroto.sensitive.get(secret)).isTrue()
        Assertions.assertThat(MemberOptionsKroto.fieldLabels.get(secret)).containsExactly("pii", "secret")
        Assertions.assertThat(MemberOptionsKroto.sensitive.get(Account.descriptor.findFieldByName("id").options)).isFalse()

        val savings = Account.descriptor.findEnumTypeByName("Kind").findValueByName("SAVINGS").options
        Assertions.assertThat(MemberOptionsKroto.legacyNumber.get(savings)).isEqualTo(7)
        Assertions.assertThat(MemberOptionsKroto.bigNumber.get(savings)).isEqualTo(9_000_000_000L)
    }

    @Test
    fun `set writes an option that get and a parsed file descriptor both see`() {
        val options = MemberOptionsKroto.access.set(DescriptorProtos.MethodOptions.newBuilder(), Access.WRITE).build()
        Assertions.assertThat(MemberOptionsKroto.access.get(options)).isEqualTo(Access.WRITE)

        val file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("synthetic.proto")
            .setPackage("synthetic")
            .addDependency(MemberOptionsKroto.descriptor.name)
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Req"))
            .addService(
                DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName("Svc")
                    .addMethod(
                        DescriptorProtos.MethodDescriptorProto.newBuilder()
                            .setName("Do")
                            .setInputType(".synthetic.Req")
                            .setOutputType(".synthetic.Req")
                            .setOptions(options)
                    )
            )
            .build()
        val built = Descriptors.FileDescriptor.buildFrom(file, arrayOf(MemberOptionsKroto.descriptor))
        Assertions.assertThat(MemberOptionsKroto.access.get(built.services.single().methods.single().options)).isEqualTo(Access.WRITE)
    }

    @Test
    fun `message-typed extensions are not exposed`() {
        val names = MemberOptionsKroto::class.java.declaredFields.map { it.name }
        Assertions.assertThat(names).contains("access", "methodNote", "sensitive", "fieldLabels", "legacyNumber", "bigNumber")
        Assertions.assertThat(names).doesNotContain("tag")
    }
}

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

import com.example.membertest.PersonServiceGrpc
import com.example.membertest.krotodc.Empty
import com.example.membertest.krotodc.Person
import com.example.membertest.krotodc.PersonServiceGrpcKroto
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class MemberGrpcDescriptorsTest {

    @Test
    fun `SERVICE_NAME and serviceDescriptor match grpc-java`() {
        Assertions.assertThat(PersonServiceGrpcKroto.SERVICE_NAME)
            .isEqualTo(PersonServiceGrpc.SERVICE_NAME)
            .isEqualTo("com.example.membertest.PersonService")
        Assertions.assertThat(PersonServiceGrpcKroto.serviceDescriptor).isSameAs(PersonServiceGrpc.getServiceDescriptor())
        Assertions.assertThat(PersonServiceGrpcKroto.serviceDescriptor.methods).hasSize(4)
    }

    @Test
    fun `method descriptors are grpc-java's`() {
        Assertions.assertThat(PersonServiceGrpcKroto.getPersonMethod).isSameAs(PersonServiceGrpc.getGetPersonMethod())
        Assertions.assertThat(PersonServiceGrpcKroto.listHTTPAddressesMethod)
            .isSameAs(PersonServiceGrpc.getListHTTPAddressesMethod())
        Assertions.assertThat(PersonServiceGrpcKroto.uploadPeopleMethod).isSameAs(PersonServiceGrpc.getUploadPeopleMethod())
        Assertions.assertThat(PersonServiceGrpcKroto.chatMethod).isSameAs(PersonServiceGrpc.getChatMethod())
        Assertions.assertThat(PersonServiceGrpcKroto.listHTTPAddressesMethod.type)
            .isEqualTo(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
    }

    @Test
    fun `FullMethodNames are upper snake constants equal to the descriptors' full method names`() {
        Assertions.assertThat(PersonServiceGrpcKroto.FullMethodNames.GET_PERSON)
            .isEqualTo(PersonServiceGrpcKroto.getPersonMethod.fullMethodName)
            .isEqualTo("com.example.membertest.PersonService/GetPerson")
        Assertions.assertThat(PersonServiceGrpcKroto.FullMethodNames.LIST_HTTP_ADDRESSES)
            .isEqualTo(PersonServiceGrpc.getListHTTPAddressesMethod().fullMethodName)
        Assertions.assertThat(PersonServiceGrpcKroto.FullMethodNames.UPLOAD_PEOPLE)
            .isEqualTo(PersonServiceGrpc.getUploadPeopleMethod().fullMethodName)
        Assertions.assertThat(PersonServiceGrpcKroto.FullMethodNames.CHAT)
            .isEqualTo(PersonServiceGrpc.getChatMethod().fullMethodName)
        Assertions.assertThat(io.grpc.MethodDescriptor.extractFullServiceName(PersonServiceGrpcKroto.FullMethodNames.CHAT))
            .isEqualTo(PersonServiceGrpcKroto.SERVICE_NAME)
    }

    @Test
    fun `accessors are static for Java callers`() {
        val kroto = PersonServiceGrpcKroto::class.java
        Assertions.assertThat(java.lang.reflect.Modifier.isStatic(kroto.getMethod("getServiceDescriptor").modifiers)).isTrue
        Assertions.assertThat(java.lang.reflect.Modifier.isStatic(kroto.getMethod("getGetPersonMethod").modifiers)).isTrue
        Assertions.assertThat(kroto.getField("SERVICE_NAME").get(null)).isEqualTo(PersonServiceGrpc.SERVICE_NAME)
    }

    private val seenMethods = CopyOnWriteArrayList<String>()

    /** An interceptor written against the krotoDC API only. */
    private val interceptor = object : ServerInterceptor {
        override fun <ReqT, RespT> interceptCall(
            call: ServerCall<ReqT, RespT>,
            headers: Metadata,
            next: ServerCallHandler<ReqT, RespT>
        ): ServerCall.Listener<ReqT> {
            val name = when (call.methodDescriptor.fullMethodName) {
                PersonServiceGrpcKroto.FullMethodNames.GET_PERSON -> "get"
                PersonServiceGrpcKroto.FullMethodNames.LIST_HTTP_ADDRESSES -> "list"
                PersonServiceGrpcKroto.FullMethodNames.UPLOAD_PEOPLE -> "upload"
                PersonServiceGrpcKroto.FullMethodNames.CHAT -> "chat"
                else -> "other"
            }
            if (call.methodDescriptor.serviceName == PersonServiceGrpcKroto.SERVICE_NAME) {
                seenMethods.add(name)
            }
            return next.startCall(call, headers)
        }
    }

    private val serviceImpl = object : PersonServiceGrpcKroto.PersonServiceCoroutineImplBase() {
        override suspend fun getPerson(request: Person): Person = request.copy(age = request.age + 1)

        override fun listHTTPAddresses(request: Person): Flow<Person.Address> = flowOf(request.home, *request.previous.toTypedArray())

        override suspend fun uploadPeople(requests: Flow<Person>): Empty {
            requests.toList()
            return Empty()
        }

        override fun chat(requests: Flow<Person>): Flow<Person> = requests.map { it.copy(name = it.name.uppercase()) }
    }

    private val serverName = InProcessServerBuilder.generateName()
    private val server: Server = InProcessServerBuilder.forName(serverName).directExecutor()
        .addService(ServerInterceptors.intercept(serviceImpl, interceptor))
        .build()
        .start()
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build()

    @AfterEach
    fun shutdown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `bindService uses the same service descriptor`() {
        Assertions.assertThat(serviceImpl.bindService().serviceDescriptor).isSameAs(PersonServiceGrpcKroto.serviceDescriptor)
        Assertions.assertThat(serviceImpl.bindService().getMethod(PersonServiceGrpcKroto.FullMethodNames.GET_PERSON))
            .isNotNull
    }

    @Test
    fun `member mode stubs and impl bases still work end to end, seen by FullMethodNames`(): Unit = runBlocking {
        val stub = PersonServiceGrpcKroto.PersonServiceCoroutineStub(channel)
        val ada = Person(name = "Ada", age = 36, home = Person.Address(city = "London"), previous = listOf(Person.Address(city = "Paris")))
        Assertions.assertThat(stub.getPerson(ada)).isEqualTo(ada.copy(age = 37))
        Assertions.assertThat(stub.listHTTPAddresses(ada).toList()).containsExactly(Person.Address(city = "London"), Person.Address(city = "Paris"))
        Assertions.assertThat(stub.uploadPeople(flowOf(ada, ada))).isInstanceOf(Empty::class.java)
        Assertions.assertThat(stub.chat(flowOf(ada)).toList().map { it.name }).containsExactly("ADA")
        Assertions.assertThat(seenMethods).containsExactly("get", "list", "upload", "chat")
        Unit
    }
}

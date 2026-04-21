/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extension.kotlin.messaging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.axonframework.extension.kotlin.ExampleCommand
import org.axonframework.messaging.commandhandling.CommandMessage
import org.axonframework.messaging.commandhandling.GenericCommandMessage
import org.axonframework.messaging.core.GenericMessage
import org.axonframework.messaging.core.Message
import org.axonframework.messaging.core.MessageStream
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.annotation.ParameterResolver
import org.axonframework.messaging.core.annotation.ParameterResolverFactory
import org.axonframework.messaging.core.unitofwork.LegacyMessageSupportingContext
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.junit.jupiter.api.Test
import java.lang.reflect.Executable
import java.lang.reflect.Parameter
import java.util.concurrent.CompletableFuture
import java.util.function.Function

internal fun topLevelCommandHandler(command: ExampleCommand): String = "top-level-${command.id}"

internal class MemberCommandHandler {
    fun handle(command: ExampleCommand): String = "member-${command.id}"
}

internal class FunctionalCommandMessageHandlingMemberTest {

    @Test
    fun `handle should invoke top-level function without instance`() {
        val member = FunctionalCommandMessageHandlingMember<Any>(
            function = ::topLevelCommandHandler,
            messageType = CommandMessage::class.java,
            returnTypeConverter = passThroughConverter(),
            parameterResolverFactory = commandPayloadResolverFactory()
        )
        val command = GenericCommandMessage(MessageType(ExampleCommand::class.java), ExampleCommand("1"))
        val context = LegacyMessageSupportingContext(command)

        val result = member.handle(command, context, null).first().asCompletableFuture().join().message().payload()

        assertThat(result).isEqualTo("top-level-1")
    }

    @Test
    fun `handle should invoke member function with instance`() {
        val handler = MemberCommandHandler()
        val member = FunctionalCommandMessageHandlingMember<MemberCommandHandler>(
            function = MemberCommandHandler::handle,
            messageType = CommandMessage::class.java,
            returnTypeConverter = passThroughConverter(),
            parameterResolverFactory = commandPayloadResolverFactory()
        )
        val command = GenericCommandMessage(MessageType(ExampleCommand::class.java), ExampleCommand("2"))
        val context = LegacyMessageSupportingContext(command)

        val result = member.handle(command, context, handler).first().asCompletableFuture().join().message().payload()

        assertThat(result).isEqualTo("member-2")
    }

    @Test
    fun `handle should fail when member function is called without instance`() {
        val member = FunctionalCommandMessageHandlingMember<MemberCommandHandler>(
            function = MemberCommandHandler::handle,
            messageType = CommandMessage::class.java,
            returnTypeConverter = passThroughConverter(),
            parameterResolverFactory = commandPayloadResolverFactory()
        )
        val command = GenericCommandMessage(MessageType(ExampleCommand::class.java), ExampleCommand("3"))
        val context = LegacyMessageSupportingContext(command)

        assertThatThrownBy {
            member.handle(command, context, null)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Instance required for member function")
    }

    private fun passThroughConverter(): Function<Any?, MessageStream<*>> =
        Function { MessageStream.just(GenericMessage(MessageType(Any::class.java), it)) }

    private fun commandPayloadResolverFactory(): ParameterResolverFactory =
        ParameterResolverFactory { _: Executable, _: Array<Parameter>, parameterIndex: Int ->
            if (parameterIndex == 0) {
                object : ParameterResolver<Any> {
                    override fun matches(processingContext: ProcessingContext): Boolean =
                        Message.fromContext(processingContext) is CommandMessage

                    override fun resolveParameterValue(processingContext: ProcessingContext): CompletableFuture<Any> {
                        val message = Message.fromContext(processingContext) as CommandMessage
                        return CompletableFuture.completedFuture(message.payload())
                    }

                    override fun supportedPayloadType(): Class<*> = ExampleCommand::class.java
                }
            } else {
                null
            }
        }
}
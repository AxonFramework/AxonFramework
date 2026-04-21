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
import org.axonframework.conversion.PassThroughConverter
import org.axonframework.extension.kotlin.ExampleCommand
import org.axonframework.messaging.commandhandling.CommandMessage
import org.axonframework.messaging.commandhandling.GenericCommandMessage
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver
import org.axonframework.messaging.core.Message
import org.axonframework.messaging.core.annotation.ParameterResolver
import org.axonframework.messaging.core.annotation.ParameterResolverFactory
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter
import org.axonframework.messaging.core.unitofwork.LegacyMessageSupportingContext
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.junit.jupiter.api.Test
import java.lang.reflect.Executable
import java.lang.reflect.Parameter
import java.util.concurrent.CompletableFuture

internal fun topLevelFunctionalCommand(command: ExampleCommand): String = "component-${command.id}"

internal class ComponentMemberHandler {
    fun handle(command: ExampleCommand): String = "component-member-${command.id}"
}

internal class FunctionalCommandHandlerComponentTest {

    @Test
    fun `handle should invoke registered top-level function`() {
        val component = FunctionalCommandHandlerComponent<Any>(
            function = ::topLevelFunctionalCommand,
            instance = null,
            parameterResolverFactory = commandPayloadResolverFactory(),
            messageTypeResolver = ClassBasedMessageTypeResolver(),
            converter = DelegatingMessageConverter(PassThroughConverter.INSTANCE)
        )
        val command = GenericCommandMessage(org.axonframework.messaging.core.MessageType(ExampleCommand::class.java), ExampleCommand("7"))
        val context = LegacyMessageSupportingContext(command)

        val result = component.handle(command, context).first().asCompletableFuture().join().message().payload()

        assertThat(result).isEqualTo("component-7")
    }

    @Test
    fun `constructor should fail for member function without instance`() {
        assertThatThrownBy {
            FunctionalCommandHandlerComponent<ComponentMemberHandler>(
                function = ComponentMemberHandler::handle,
                instance = null,
                parameterResolverFactory = commandPayloadResolverFactory(),
                messageTypeResolver = ClassBasedMessageTypeResolver(),
                converter = DelegatingMessageConverter(PassThroughConverter.INSTANCE)
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Member functions must be used on object instance")
    }

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
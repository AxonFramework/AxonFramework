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

import org.axonframework.common.configuration.Configuration
import org.axonframework.extension.kotlin.common.isTopLevel
import org.axonframework.messaging.commandhandling.*
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.MessageTypeResolver
import org.axonframework.messaging.core.QualifiedName
import org.axonframework.messaging.core.annotation.MessageStreamResolverUtils
import org.axonframework.messaging.core.annotation.ParameterResolverFactory
import org.axonframework.messaging.core.conversion.MessageConverter
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import java.util.function.Function
import kotlin.reflect.KFunction

/**
 * Functional command handling component.
 * @param <T> type of instance to operate on. May be omitted if the function is a top level function.
 * @param function function to call.
 * @param instance optional instance.
 * @param parameterResolverFactory resolver for function parameters.
 * @param messageTypeResolver resolver for the type of message.
 * @param converter converter for the payload.
 */
class FunctionalCommandHandlerComponent<T : Any> private constructor(
    function: KFunction<*>,
    instance: T?,
    parameterResolverFactory: ParameterResolverFactory,
    messageTypeResolver: MessageTypeResolver,
    converter: MessageConverter,
    private val handlingComponent: SimpleCommandHandlingComponent
) : CommandHandlingComponent by handlingComponent {

    constructor(function: KFunction<*>, configuration: Configuration, instance: T? = null) : this(
        function = function,
        instance = instance,
        parameterResolverFactory = configuration.getComponent(ParameterResolverFactory::class.java),
        messageTypeResolver = configuration.getComponent(MessageTypeResolver::class.java),
        converter = configuration.getComponent(MessageConverter::class.java)
    )

    constructor(
        function: KFunction<*>,
        parameterResolverFactory: ParameterResolverFactory,
        messageTypeResolver: MessageTypeResolver,
        converter: MessageConverter,
        instance: T? = null,
    ) : this(
        function = function,
        instance = instance,
        parameterResolverFactory = parameterResolverFactory,
        messageTypeResolver = messageTypeResolver,
        converter = converter,
        handlingComponent = SimpleCommandHandlingComponent.create("FunctionalCommandHandlingComponent${function.name}")
    )

    init {
        if (!function.isTopLevel()) {
            requireNotNull(instance) { "Member functions must be used on object instance, but none was provided." }
        }
        val member = FunctionalCommandMessageHandlingMember<T>(
            function = function,
            messageType = CommandMessage::class.java,
            parameterResolverFactory = parameterResolverFactory,
            returnTypeConverter = Function {
                MessageStreamResolverUtils.resolveToStream(it, ClassBasedMessageTypeResolver())
            }
        )
        val payloadType = member.payloadType()
        // always deduct qualified name from the payload
        val qualifiedName: QualifiedName = messageTypeResolver.resolve(payloadType)
            .orElse(MessageType(payloadType))
            .qualifiedName()

        handlingComponent.subscribe(qualifiedName) { command: CommandMessage, ctx: ProcessingContext ->
            val result = member.handle(command.withConvertedPayload(payloadType, converter), ctx, instance)
            result.mapMessage {
                it as? CommandResultMessage ?: GenericCommandResultMessage(it)
            }.first().cast()
        }
    }
}

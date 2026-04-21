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

import org.axonframework.common.annotation.AnnotationUtils
import org.axonframework.extension.kotlin.common.isTopLevel
import org.axonframework.messaging.core.Message
import org.axonframework.messaging.core.MessageStream
import org.axonframework.messaging.core.annotation.MessageHandler
import org.axonframework.messaging.core.annotation.MessageHandlerInvocationException
import org.axonframework.messaging.core.annotation.MessageHandlingMember
import org.axonframework.messaging.core.annotation.ParameterResolver
import org.axonframework.messaging.core.annotation.ParameterResolverFactory
import org.axonframework.messaging.core.annotation.UnsupportedHandlerException
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Parameter
import java.util.Optional
import java.util.concurrent.ExecutionException
import java.util.function.Function
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class FunctionalCommandMessageHandlingMember<T : Any>(
    val function: KFunction<*>,
    val messageType: Class<out Message>,
    val returnTypeConverter: Function<Any?, MessageStream<*>>,
    parameterResolverFactory: ParameterResolverFactory
) : MessageHandlingMember<T> {

    val parameterCount: Int
    val resolvers: Array<ParameterResolver<*>>
    val payloadType: Class<*>

    init {
        require(function.parameters.isNotEmpty()) { "The command handler must receive at least a command parameter" }
        // parameter resolution must be performed on a Java Method (it relies on ParameterResolverFactory using Executable)
        val method =
            requireNotNull(function.javaMethod) { "Kotlin function ${function.name} must correspond to a java method" }
        this.parameterCount = method.parameterCount
        val parameters: Array<Parameter> = method.parameters
        val parameterResolvers: Array<ParameterResolver<*>?> = arrayOfNulls<ParameterResolver<*>>(parameterCount)
        var supportedPayloadType: Class<*> = AnnotationUtils
            .findAnnotationAttribute<Class<*>>(
                method,
                MessageHandler::class.java,
                "payloadType"
            ) // try to read from annotation
            .orElseGet { parameters[0].type } // or just use first parameter type
        for (i in 0..<parameterCount) {
            val parameterResolver = parameterResolverFactory.createInstance(method, parameters, i)
            parameterResolvers[i] = parameterResolver
            if (parameterResolver == null) {
                throw UnsupportedHandlerException(
                    "Unable to resolve parameter $i (${
                        parameters[i].getType().getSimpleName()
                    }) in handler ${method.toGenericString()} .", method
                )
            } else {
                if (supportedPayloadType.isAssignableFrom(parameterResolver.supportedPayloadType())) {
                    supportedPayloadType = parameterResolver.supportedPayloadType()
                } else if (!parameterResolver.supportedPayloadType().isAssignableFrom(supportedPayloadType)) {
                    throw UnsupportedHandlerException(
                        "The method ${method.toGenericString()} seems to have parameters that put conflicting requirements on the payload type" +
                                " applicable on that method: $supportedPayloadType vs ${parameterResolver.supportedPayloadType()}",
                        method
                    )
                }
            }
        }
        this.payloadType = supportedPayloadType
        this.resolvers = parameterResolvers.filterNotNull().toTypedArray()
    }

    override fun payloadType(): Class<*> = payloadType

    override fun canHandle(message: Message, context: ProcessingContext): Boolean {
        val contextWithMessage = Message.addToContext(context, message)
        return typeMatches(message)
                && payloadType.isAssignableFrom(message.payloadType())
                && parametersMatch(message, contextWithMessage)
    }

    override fun canHandleMessageType(messageType: Class<out Message>): Boolean {
        return this.payloadType.isAssignableFrom(payloadType)
    }

    @Deprecated(message = "left over from sync version", level = DeprecationLevel.WARNING)
    override fun handleSync(message: Message, context: ProcessingContext, target: T?): Any =
        try {
            handle(message, context, target).first().asCompletableFuture().get()?.message()?.payload()!!
        } catch (e: ExecutionException) {
            if (e.cause is Exception) {
                throw e.cause!!
            } else {
                throw e
            }
        }

    override fun handle(message: Message, context: ProcessingContext, target: T?): MessageStream<*> {
        return try {
            val paramValues = resolveParameterValues(Message.addToContext(context, message))
            val result = function.invokeFunctionAuto(instance = target, args = paramValues)
            returnTypeConverter.apply(result)
        } catch (e: Exception) {
            when (e) {
                is InvocationTargetException, is IllegalAccessException -> {
                    if (e.cause is java.lang.Exception || e.cause is Error) {
                        MessageStream.failed<Message>(e.cause!!)
                    } else {
                        MessageStream.failed<Message>(
                            MessageHandlerInvocationException("Error handling an object of type $messageType", e)
                        )
                    }
                }

                else -> throw e
            }
        }
    }

    private fun resolveParameterValues(context: ProcessingContext): Array<Any?> {
        val params = arrayOfNulls<Any>(parameterCount)
        for (i in 0..<parameterCount) {
            params[i] = resolvers[i].resolveParameterValue(context).get()
        }
        return params
    }

    /**
     * For a functional handler there is no unwrapping.
     */
    override fun <HT : Any> unwrap(handlerType: Class<HT>): Optional<HT> {
        return Optional.empty<HT>()
    }

    /**
     * Checks if this member can handle the type of the given `message`. This method does not check if the
     * parameter resolvers of this member are compatible with the given message. Use
     * [.parametersMatch] for that.
     *
     * @param message the message to check for
     * @return `true` if this member can handle the message type. `false` otherwise
     */
    fun typeMatches(message: Message): Boolean = messageType.isInstance(message)

    /**
     * Checks if the parameter resolvers of this member are compatible with the given `message` and `context`.
     *
     * @param message the message to check for.
     * @param processingContext context to check for.
     * @return `true` if the parameter resolvers can handle this message and context. `false` otherwise
     */
    fun parametersMatch(message: Message, processingContext: ProcessingContext): Boolean {
        for (resolver in resolvers) {
            if (!resolver.matches(processingContext)) {
                return false
            }
        }
        return true
    }

    /**
     * Invoke with or without instance depending on the function declaration.
     * @param instance nullable instance.
     * @param args list of parameters excluding the instance.
     * @return nullable return of the function.
     */
    private fun KFunction<*>.invokeFunctionAuto(
        instance: Any?,
        vararg args: Any?
    ): Any? {
        return if (this.isTopLevel()) {
            this.call(*args)
        } else {
            requireNotNull(instance) { "Instance required for member function ${this.name}" }
            this.call(instance, *args)
        }
    }
}
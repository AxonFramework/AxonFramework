/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.Optional;

/**
 * Interface describing a handler for specific messages targeting entities of a specific type.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message
 * @author Allard Buijze
 * @since 3.0
 */
public interface MessageHandlingMember<T> {

    /**
     * Returns the payload type of messages that can be processed by this handler.
     *
     * @return The payload type of messages expected by this handler
     */
    Class<?> payloadType();

    /**
     * Returns a number representing the priority of this handler over other handlers capable of processing the same
     * message.
     * <p>
     * In general, a handler with a higher priority will receive the message before (or instead of) handlers with a
     * lower priority. However, the priority value may not be the only indicator that is used to determine the order of
     * invocation. For instance, a message processor may decide to ignore the priority value if one message handler is a
     * more specific handler of the message than another handler.
     *
     * @return Number indicating the priority of this handler over other handlers
     */
    default int priority() {
        return 0;
    }

    /**
     * Checks if this handler is capable of handling the given {@code message}.
     *
     * @param message           The message that is to be handled
     * @param processingContext
     * @return {@code true} if the handler is capable of handling the message, {@code false} otherwise
     */
    // TODO - ProcessingContext should eventually become non-null when canHandle for event handlers is based on fully-qualified message name only
    boolean canHandle(@Nonnull Message<?> message, @Nullable ProcessingContext processingContext);

    /**
     * Checks if this handler is capable of handling messages with the given {@code payloadType}.
     *
     * @param payloadType The payloadType of a message that is to be handled
     * @return {@code true} if the handler is capable of handling the message with given type, {@code false} otherwise
     */
    default boolean canHandleType(@Nonnull Class<?> payloadType) {
        return true;
    }

    /**
     * Checks if this handlers is capable of handling {@link Message} implementations of the given {@code messageType}.
     * <p>
     * It is recommended to suppress the raw type use validation of the {@code messageType} parameter when implementing
     * this method, as usage of this method with a {@code Message} generic would required reflection or casting
     * otherwise.
     *
     * @param messageType the {@link Message}'s type to check if it can be handled by this handler
     * @return {@code true} if this handler can handle the given {@code messageType}, {@code false} otherwise
     */
    @SuppressWarnings("rawtypes")
    boolean canHandleMessageType(@Nonnull Class<? extends Message> messageType);

    /**
     * Handles the given {@code message} by invoking the appropriate method on given {@code target}. This may result in
     * an exception if the given target is not capable of handling the message or if an exception is thrown during
     * invocation of the method.
     *
     * @param message The message to handle
     * @param target  The target to handle the message
     * @return The message handling result in case the invocation was successful
     * @throws Exception when there was a problem that prevented invocation of the method or if an exception was thrown
     *                   from the invoked method
     */
    @Deprecated
    Object handleSync(@Nonnull Message<?> message, @Nullable T target) throws Exception;

    /**
     * TODO add documentation
     */
    default MessageStream<?> handle(@Nonnull Message<?> message,
                                    @Nonnull ProcessingContext processingContext,
                                    @Nullable T target) {
        try {
            // TODO: 24-11-2023 proper impl
            return MessageStream.just(GenericMessage.asMessage(handleSync(message, target)));
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    /**
     * Returns the wrapped handler object if its type is an instance of the given {@code handlerType}. For instance, if
     * this method is invoked with {@link java.lang.reflect.Executable} and the message is handled by a method of the
     * target entity, then this method will return the method handle as a {@link java.lang.reflect.Method}.
     *
     * @param handlerType The expected type of the wrapped handler
     * @param <HT>        The wrapped handler type
     * @return An Optional containing the wrapped handler object or an empty Optional if the handler is not an instance
     * of the given handlerType
     */
    <HT> Optional<HT> unwrap(Class<HT> handlerType);

    /**
     * Gets the declaring class of this Message Handling Member.
     *
     * @return the declaring class of this Message Handling Member
     */
    default Class<?> declaringClass() {
        return unwrap(Member.class).map(Member::getDeclaringClass)
                                   .orElseThrow(() -> new UnsupportedOperationException(
                                           "This implementation of MessageHandlingMember does not wrap a "
                                                   + "java.lang.reflect.Member. Please provide a different way of "
                                                   + "getting 'declaringClass' of this MessageHandlingMember."));
    }

    /**
     * Returns the signature of the member. This may be used in logging or exceptions to demarcate the actual class
     * member invoked. If this member does not have a signature, {@code "__unknown__"} is returned.
     *
     * @return the signature of the handling member
     */
    default String signature() {
        return unwrap(Executable.class).map(Executable::toGenericString)
                                       .orElse("__unknown__");
    }

    /**
     * Retrieve a single attributes for the given {@code attributeKey}. If this {@link MessageHandlingMember} does not
     * hold a value referencing the {@code attributeKey}, an {@link Optional#empty()} is returned. Otherwise, a
     * non-empty {@link Optional} containing the attribute will be provided.
     * <p>
     * When using the method, consider checking the {@link org.axonframework.messaging.HandlerAttributes} for a list of
     * common handler attributes.
     *
     * @param attributeKey the key to retrieve an attribute for
     * @return a non-empty {@link Optional} of the attribute referencing the given {@code attributeKey}. Otherwise, an
     * {@link Optional#empty()} will be returned
     */
    default <R> Optional<R> attribute(String attributeKey) {
        return Optional.empty();
    }
}

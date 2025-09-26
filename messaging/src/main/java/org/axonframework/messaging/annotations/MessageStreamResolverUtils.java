/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.annotations;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.util.ClasspathResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class that can resolve the result of any {@link org.axonframework.messaging.configuration.MessageHandler}
 * into the expected corresponding {@link MessageStream}.
 *
 * @author Simon Zambrovski
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class MessageStreamResolverUtils {

    /**
     * Resolves the given {@code result} into a {@link MessageStream}, using the {@code typeResolver} when a
     * {@link org.axonframework.messaging.Message} is constructed to define the {@link MessageType}.
     * <p>
     * Is able to switch between {@link Optional}, {@link CompletableFuture}, {@link Iterable}, {@link Stream},
     * {@link Mono}, and {@link Flux}. If none of the above apply, or the given {@code result} is {@code null},
     * {@link MessageStream#just(Message)} will be used.
     *
     * @param result       The result to map into a {@link MessageStream}.
     * @param typeResolver The {@code MessageTypeResolver} used to resolve the {@link MessageType} for
     *                     {@link org.axonframework.messaging.Message Messages} that are held in the returned
     *                     {@link MessageStream}.
     * @return A {@code MessageStream} based on the given {@code result}.
     */
    public static MessageStream<?> resolveToStream(@Nullable Object result,
                                                   @Nonnull MessageTypeResolver typeResolver) {
        Objects.requireNonNull(typeResolver, "The Message Type Resolver must not be null.");
        if (result == null) {
            //noinspection ConstantValue
            MessageType resultType = typeResolver.resolveOrThrow(ObjectUtils.nullSafeTypeOf(result));
            return MessageStream.just(new GenericMessage(resultType, null));
        }

        // Handle Project Reactor types first with traditional if-statements
        if (ClasspathResolver.projectReactorOnClasspath()) {
            if (result instanceof Mono<?> mono) {
                return MessageStream.fromMono(mono.map(r -> new GenericMessage(typeResolver.resolveOrThrow(r), r)));
            }
            if (result instanceof Flux<?> flux) {
                return MessageStream.fromFlux(flux.map(r -> new GenericMessage(typeResolver.resolveOrThrow(r), r)));
            }
        }

        // Handle standard types with pattern matching switch
        return switch (result) {
            case MessageStream<?> messageStream -> messageStream;
            case CompletableFuture<?> future -> MessageStream.fromFuture(
                    future.thenApply(r -> new GenericMessage(new MessageType(r.getClass()), r))
            );
            case Optional<?> optional when optional.isPresent() -> {
                Object r = optional.get();
                yield MessageStream.just(new GenericMessage(typeResolver.resolveOrThrow(r), r));
            }
            case Optional<?> empty -> MessageStream.empty();
            case Iterable<?> iterable -> MessageStream.fromStream(
                    StreamSupport.stream(iterable.spliterator(), false)
                                 .map(r -> new GenericMessage(typeResolver.resolveOrThrow(r), r))
            );
            case Stream<?> stream -> MessageStream.fromStream(
                    stream.map(r -> new GenericMessage(typeResolver.resolveOrThrow(r), r))
            );
            default -> MessageStream.just(
                    new GenericMessage(new MessageType(ObjectUtils.nullSafeTypeOf(result)), result)
            );
        };
    }

    private MessageStreamResolverUtils() {
        // Utility class
    }
}

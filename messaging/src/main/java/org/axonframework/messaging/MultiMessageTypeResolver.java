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

package org.axonframework.messaging;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A {@link MessageTypeResolver} implementation that delegates to a list of other resolvers, trying each in sequence
 * until one successfully resolves the message type.
 * <p>
 * This resolver provides the ability to combine multiple resolvers through the {@link #of(MessageTypeResolver...)} factory method.
 * <p>
 * Example usage:
 * <pre>
 * MultiMessageTypeResolver resolver = MultiMessageTypeResolver.of(
 *     namespace("com.example.commands")
 *         .message(CreateUser.class, "createUser", "1.0.0")
 *         .message(UpdateUser.class, "updateUser", "1.0.0"),
 *     namespace("com.example.events")
 *         .message(UserCreated.class, "userCreated", "1.0.0")
 * );
 * </pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MultiMessageTypeResolver implements MessageTypeResolver {

    private final List<MessageTypeResolver> delegates;

    /**
     * Creates a new MultiMessageTypeResolver with the given list of delegate resolvers.
     * <p>
     * Delegates are tried in the order they appear in the list.
     *
     * @param delegates The list of delegate resolvers to use in sequence.
     */
    private MultiMessageTypeResolver(List<MessageTypeResolver> delegates) {
        this.delegates = Objects.requireNonNull(delegates, "Delegates may not be null");
    }

    /**
     * Creates a new MultiMessageTypeResolver with the given list of delegate resolvers.
     * <p>
     * Delegates are tried in the order they appear in the list.
     *
     * @param delegates The list of delegate resolvers to use in sequence.
     * @return A new MultiMessageTypeResolver that delegates to the given resolvers.
     */
    public static MultiMessageTypeResolver of(@Nonnull List<MessageTypeResolver> delegates) {
        return new MultiMessageTypeResolver(delegates);
    }

    /**
     * Creates a new MultiMessageTypeResolver with the given delegate resolvers.
     * <p>
     * Delegates are tried in the order they appear in the parameter list.
     *
     * @param delegates The delegate resolvers to use in sequence.
     * @return A new MultiMessageTypeResolver that delegates to the given resolvers.
     */
    public static MultiMessageTypeResolver of(@Nonnull MessageTypeResolver... delegates) {
        return new MultiMessageTypeResolver(Arrays.asList(delegates));
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        MessageType result = null;
        for (var delegate : delegates) {
            try {
                result = delegate.resolve(payloadType);
            } catch (MessageTypeNotResolvedException ignored) {

            }
        }
        if (result == null) {
            throw new MessageTypeNotResolvedException(
                    "No MessageType found for payload type [" + payloadType.getName() + "]");
        }
        return result;
    }
}

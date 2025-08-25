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

package org.axonframework.spring.authorization;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * A {@link MessageDispatchInterceptor} that adds the {$code username} and {$code authorities} from the authorized
 * principle.
 *
 * @param <T> The message type this interceptor can process
 * @author Roald Bankras
 * @since 4.11.0
 */
public class MessageAuthorizationDispatchInterceptor<T extends Message> implements MessageDispatchInterceptor<T> {

    private static final Logger logger = LoggerFactory.getLogger(MessageAuthorizationDispatchInterceptor.class);

    private final Converter converter;

    /**
     * Construct a {@code MessageAuthorizationDispatchInterceptor} using the given {@code converter} to convert the
     * {@link Authentication#getPrincipal() principal} and {@link Authentication#getAuthorities()} before they are
     * stored in the {@link org.axonframework.messaging.MetaData} of the outgoing {@link Message} of type {@code T}.
     *
     * @param converter The {@code Converter} converting the {@link Authentication#getPrincipal() principal} and
     *                  {@link Authentication#getAuthorities()} before they go into the
     *                  {@link org.axonframework.messaging.MetaData} of the outgoing {@link Message} of type {@code T}.
     */
    public MessageAuthorizationDispatchInterceptor(@Nonnull Converter converter) {
        this.converter = Objects.requireNonNull(converter, "Converter must not be null.");
    }

    @Nonnull
    @Override
    public T handle(@Nonnull T message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            logger.debug("No authentication found.");
            return message;
        }

        logger.debug("Adding message metadata for username & authorities.");
        Map<String, String> authenticationDetails = new java.util.HashMap<>();
        authenticationDetails.put("username", converter.convert(authentication.getPrincipal(), String.class));
        String authorities = authentication.getAuthorities()
                                           .stream()
                                           .map(GrantedAuthority::getAuthority)
                                           .collect(Collectors.joining(","));
        authenticationDetails.put("authorities", authorities);
        //noinspection unchecked
        return (T) message.andMetaData(authenticationDetails);
    }

    @Nonnull
    @Override
    public BiFunction<Integer, T, T> handle(@Nonnull List<? extends T> list) {
        return (position, message) -> handle(message);
    }
}

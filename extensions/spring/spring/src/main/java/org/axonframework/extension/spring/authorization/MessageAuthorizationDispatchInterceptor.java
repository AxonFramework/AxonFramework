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

package org.axonframework.extension.spring.authorization;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.conversion.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Objects;
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
     * stored in the {@link Metadata} of the outgoing {@link Message} of type {@code T}.
     *
     * @param converter The {@code Converter} converting the {@link Authentication#getPrincipal() principal} and
     *                  {@link Authentication#getAuthorities()} before they go into the
     *                  {@link Metadata} of the outgoing {@link Message} of type {@code T}.
     */
    public MessageAuthorizationDispatchInterceptor(@Nonnull Converter converter) {
        this.converter = Objects.requireNonNull(converter, "Converter must not be null.");
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnDispatch(@Nonnull T message,
                                                @Nullable ProcessingContext context,
                                                @Nonnull MessageDispatchInterceptorChain<T> interceptorChain) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            logger.debug("No authentication found.");
            return interceptorChain.proceed(message, context);
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
        return interceptorChain.proceed((T) message.andMetadata(authenticationDetails), context);
    }
}

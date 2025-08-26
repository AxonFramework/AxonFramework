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
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * A {@link MessageHandlerInterceptor} that verifies authorization based on {@link Secured} annotations on the payload
 * of {@link Message Messages}.
 *
 * @param <T> The message type this interceptor can process
 * @author Roald Bankras
 * @since 4.11.0
 */
public class MessageAuthorizationHandlerInterceptor<M extends Message> implements MessageHandlerInterceptor<M> {

    /**
     * Metadata key for authorities.
     */
    public static final String METADATA_AUTHORITIES_KEY = "authorities";
    private static final Logger logger = LoggerFactory.getLogger(MessageAuthorizationHandlerInterceptor.class);

    @Override
    @Nonnull
    public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
        if (!AnnotationUtils.isAnnotationPresent(message.payloadType(), Secured.class)) {
            return interceptorChain.proceed(message, context);
        }
        Secured annotation = message.payloadType().getAnnotation(Secured.class);
        Set<String> requiredAuthorities = Arrays.stream(annotation.value()).collect(Collectors.toSet());
        try {
            Set<String> messageAuthorities =
                    Optional.ofNullable(message.metaData().get(METADATA_AUTHORITIES_KEY))
                            .map(authorityMetaData -> {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Found authorities [{}]", authorityMetaData);
                                }
                                return new HashSet<>(Arrays.asList(authorityMetaData.split(",")));
                            })
                            .orElseThrow(() -> new UnauthorizedMessageException(
                                    "No authorities found for message with identifier [" + message.identifier() + "]"
                            ));

            if (logger.isDebugEnabled()) {
                logger.debug("Authorizing for [{}] and [{}]", message.type().name(), annotation.value());
            }

            messageAuthorities.retainAll(requiredAuthorities);
            if (!messageAuthorities.isEmpty()) {
                return interceptorChain.proceed(message, context);
            }
            throw new UnauthorizedMessageException(
                    "Unauthorized message with identifier [" + message.identifier() + "]"
            );
        } catch (UnauthorizedMessageException e) {
            return MessageStream.failed(new UnauthorizedMessageException(
                    "Unauthorized message with identifier [" + message.identifier() + "]"
            ));
        }
    }
}


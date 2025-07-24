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

package org.axonframework.spring.authorization;

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


/**
 * A {@link MessageHandlerInterceptor} that verifies authorization based on {@link Secured} annotations on the payload
 * of {@link Message Messages}.
 *
 * @author Roald Bankras
 * @since 4.11.0
 */
public class MessageAuthorizationHandlerInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T> {

    private static final Logger logger = LoggerFactory.getLogger(MessageAuthorizationHandlerInterceptor.class);

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork,
                         @Nonnull InterceptorChain interceptorChain) throws Exception {
        T message = unitOfWork.getMessage();
        if (!AnnotationUtils.isAnnotationPresent(message.getPayloadType(), Secured.class)) {
            return interceptorChain.proceed();
        }
        Secured annotation = message.getPayloadType()
                                    .getAnnotation(Secured.class);

        Set<String> authorities =
                Optional.ofNullable(message.getMetaData().get("authorities"))
                        .map(authorityMetaData -> {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Found authorities [{}]", authorityMetaData);
                            }
                            return new HashSet<>(Arrays.asList(
                                    ((String) message.getMetaData().get("authorities")).split(",")
                            ));
                        })
                        .orElseThrow(() -> new UnauthorizedMessageException(
                                "No authorities found for message with identifier [" + message.getIdentifier() + "]"
                        ));

        if (logger.isDebugEnabled()) {
            logger.debug("Authorizing for [{}] and [{}]", message.getPayloadType().getName(), annotation.value());
        }

        authorities.retainAll(Arrays.stream(annotation.value()).collect(Collectors.toSet()));
        if (!authorities.isEmpty()) {
            return interceptorChain.proceed();
        }
        throw new UnauthorizedMessageException(
                "Unauthorized message with identifier [" + message.getIdentifier() + "]"
        );
    }
}


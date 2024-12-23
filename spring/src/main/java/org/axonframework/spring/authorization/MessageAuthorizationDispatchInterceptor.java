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

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A {@link MessageDispatchInterceptor} that adds the {$code username} and {$code authorities} from the authorized
 * principle.
 *
 * @author Roald Bankras
 * @since 4.11.0
 */
public class MessageAuthorizationDispatchInterceptor<T extends Message<?>> implements MessageDispatchInterceptor<T> {

    private static final Logger logger = LoggerFactory.getLogger(MessageAuthorizationDispatchInterceptor.class);

    @Nonnull
    @Override
    public T handle(@Nonnull T message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            logger.debug("No authentication found.");
            return message;
        }

        logger.debug("Adding message metadata for username & authorities.");
        Map<String, Object> authenticationDetails = new java.util.HashMap<>();
        authenticationDetails.put("username", authentication.getPrincipal());
        authenticationDetails.put("authorities", authentication.getAuthorities());
        //noinspection unchecked
        return (T) message.andMetaData(authenticationDetails);
    }

    @Nonnull
    @Override
    public BiFunction<Integer, T, T> handle(@Nonnull List<? extends T> list) {
        return (position, message) -> handle(message);
    }
}

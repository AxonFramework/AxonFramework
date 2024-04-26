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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;


/**
 * Message interceptor that verifies authorization based on {@code @PreAuthorize} annotations on commands
 *
 * @author Roald Bankras
 *
 * @since 4.10.0
 */
public class MessageAuthorizationInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T> {

    private static final Logger log = LoggerFactory.getLogger(MessageAuthorizationInterceptor.class);

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
        throws Exception {
        T message = unitOfWork.getMessage();
        if(! AnnotationUtils.isAnnotationPresent(message.getPayloadType(), PreAuthorize.class)) {
            return interceptorChain.proceed();
        }
        PreAuthorize annotation = message.getPayloadType().getAnnotation(PreAuthorize.class);
        Set<GrantedAuthority> userId = Optional.ofNullable(message.getMetaData().get("authorities"))
                                               .map(uId -> {
                                                   log.debug("Found authorities: {}", uId);
                                                   return new HashSet<>((List<GrantedAuthority>) uId);
                                               })
                                               .orElseThrow(() -> new UnauthorizedMessageException(
                                                       "No authorities found"));

        log.debug("Authorizing for {} and {}", message.getPayloadType().getName(), annotation.value());
        if (userId.contains(new SimpleGrantedAuthority(annotation.value()))) {
            return interceptorChain.proceed();
        }
        throw new UnauthorizedMessageException("Unauthorized message " + message.getIdentifier());
    }
}


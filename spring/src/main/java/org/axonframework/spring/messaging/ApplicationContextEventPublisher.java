/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.messaging;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;

import javax.annotation.Nonnull;

/**
 * Component that forward events received from a {@link SubscribableMessageSource} as Spring {@link ApplicationEvent} to
 * the ApplicationContext.
 */
public class ApplicationContextEventPublisher implements InitializingBean, ApplicationContextAware {

    private final SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private ApplicationContext applicationContext;

    /**
     * Initialize the publisher to forward events received from the given {@code messageSource} to the application
     * context that this bean is part of.
     *
     * @param messageSource The source to subscribe to.
     */
    public ApplicationContextEventPublisher(SubscribableMessageSource<? extends EventMessage<?>> messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Converts the given Axon {@code eventMessage} to a Spring ApplicationEvent. This method may be overridden to
     * change the translation.
     * <p>
     * The default implementation creates a {@link PayloadApplicationEvent} with the Message's payload.
     * <p>
     * If this method returns {@code null}, no message is published
     *
     * @param eventMessage The EventMessage to transform
     * @return the Spring ApplicationEvent representing the Axon EventMessage
     */
    protected ApplicationEvent convert(EventMessage<?> eventMessage) {
        return new PayloadApplicationEvent<>(messageSource, eventMessage.getPayload());
    }

    @Override
    public void afterPropertiesSet() {
        messageSource.subscribe(msgs -> msgs.forEach(msg -> {
            ApplicationEvent converted = convert(msg);
            if (converted != null) {
                applicationContext.publishEvent(convert(msg));
            }
        }));
    }
}

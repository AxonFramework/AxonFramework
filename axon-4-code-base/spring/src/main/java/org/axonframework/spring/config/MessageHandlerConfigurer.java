/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import javax.annotation.Nonnull;

/**
 * An implementation of a {@link ConfigurerModule} that will register a list of beans as handlers for a specific type of
 * message.
 * <p>
 * The beans will be lazily resolved to avoid circular dependencies if any these beans relies on the Axon {@link
 * org.axonframework.config.Configuration} to be available in the Application Context.
 * <p>
 * Typically, an application context would have an instance of this class registered for each type of message to
 * register.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class MessageHandlerConfigurer implements ConfigurerModule, ApplicationContextAware {

    private final Type type;
    private final List<String> handlerBeans;
    private ApplicationContext applicationContext;

    /**
     * Registers the beans identified in given {@code beanRefs} as the given {@code type} of handler with the Axon
     * {@link org.axonframework.config.Configuration}.
     *
     * @param type     The type of handler to register the beans as.
     * @param beanRefs A list of bean identifiers to register.
     */
    public MessageHandlerConfigurer(Type type, List<String> beanRefs) {
        this.type = type;
        this.handlerBeans = beanRefs;
    }

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        switch (type) {
            case EVENT:
                handlerBeans.forEach(handler -> configurer.registerEventHandler(c -> applicationContext.getBean(handler)));
                break;
            case QUERY:
                handlerBeans.forEach(handler -> configurer.registerQueryHandler(c -> applicationContext.getBean(handler)));
                break;
            case COMMAND:
                handlerBeans.forEach(handler -> configurer.registerCommandHandler(c -> applicationContext.getBean(
                        handler)));
                break;
        }
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Enumeration defining the auto configurable message handler types.
     */
    public enum Type {

        /**
         * Lists the message handler for {@link CommandMessage CommandMessages}.
         */
        COMMAND(CommandMessage.class),
        /**
         * Lists the message handler for {@link EventMessage EventMessages}.
         */
        EVENT(EventMessage.class),
        /**
         * Lists the message handler for {@link QueryMessage QueryMessages}.
         */
        QUERY(QueryMessage.class);

        private final Class<? extends Message<?>> messageType;

        // Suppressed to allow instantiation of enumeration.
        @SuppressWarnings({"rawtypes", "unchecked"})
        Type(Class<? extends Message> messageType) {
            this.messageType = (Class<? extends Message<?>>) messageType;
        }

        /**
         * Returns the supported {@link Message} implementation.
         *
         * @return The supported {@link Message} implementation.
         */
        public Class<? extends Message<?>> getMessageType() {
            return messageType;
        }
    }
}

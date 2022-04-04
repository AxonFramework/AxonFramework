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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessageHandler;
import org.axonframework.messaging.MessageHandler;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;
import javax.annotation.Nonnull;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * Registers Spring beans that implement both MessageHandler and SupportedCommandNamesAware with the command bus.
 *
 * @author Allard Buijze
 * @since 3.0
 * @deprecated Replaced by the {@link MessageHandlerLookup} and {@link MessageHandlerConfigurer}.
 */
@Deprecated
public class CommandHandlerSubscriber implements ApplicationContextAware, SmartLifecycle {

    private ApplicationContext applicationContext;
    private boolean started;
    private Collection<MessageHandler> commandHandlers;
    private CommandBus commandBus;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the command bus to use when subscribing command handlers. If not set the command bus is taken from Spring's
     * application context.
     *
     * @param commandBus the command bus to use when subscribing handlers
     */
    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Sets the command handlers to subscribe to the bus. If not set the command handlers are taken from Spring's
     * application context by scanning for beans of type {@link MessageHandler} that can handle commands.
     *
     * @param commandHandlers command handlers to subscribe to the command bus
     */
    public void setCommandHandlers(Collection<MessageHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start() {
        if (commandBus == null && !applicationContext.getBeansOfType(CommandBus.class).isEmpty()) {
            commandBus = applicationContext.getBean(CommandBus.class);
        }
        if (commandHandlers == null) {
            commandHandlers = beansOfTypeIncludingAncestors(applicationContext, MessageHandler.class).values();
        }
        commandHandlers.stream().filter(commandHandler -> commandHandler instanceof CommandMessageHandler)
                .forEach(commandHandler -> {
                    for (String commandName : ((CommandMessageHandler) commandHandler).supportedCommandNames()) {
                        commandBus.subscribe(commandName, commandHandler);
                    }
                });
        this.started = true;
    }


    @Override
    public void stop() {
        this.started = false;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE / 2;
    }
}

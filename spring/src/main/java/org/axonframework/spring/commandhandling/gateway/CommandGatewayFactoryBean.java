/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.CommandGatewayFactory;
import org.axonframework.commandhandling.gateway.RetryScheduler;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * FactoryBean that creates a gateway instance for any given (compatible) interface. If no explicit interface is
 * provided, the {@link CommandGateway} interface is assumed.
 * <p/>
 * For details about the structure of compatible interfaces, see {@link CommandGatewayFactory}.
 *
 * @param <T> The type of gateway to be created by this factory bean. Note that the correct interface must also be set
 *            using {@link #setGatewayInterface(Class)}. Failure to do so may result in class cast exceptions.
 * @author Allard Buijze
 * @see CommandGatewayFactory
 * @since 2.0
 */
public class CommandGatewayFactoryBean<T> implements FactoryBean<T>, InitializingBean {

    private CommandBus commandBus;
    private RetryScheduler retryScheduler;
    private List<MessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors = new ArrayList<>();
    private List<CommandCallback<?, ?>> commandCallbacks = new ArrayList<>();
    private T gateway;
    private Class<T> gatewayInterface;

    @Override
    public T getObject() throws Exception {
        if (gateway == null) {
            throw new FactoryBeanNotInitializedException();
        }
        return gateway;
    }

    @Override
    public Class<?> getObjectType() {
        return gatewayInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void afterPropertiesSet() throws Exception {
        if (commandBus == null) {
            throw new AxonConfigurationException("CommandBus may not be null");
        }
        if (gatewayInterface == null) {
            gatewayInterface = (Class<T>) CommandGateway.class;
        }
        final CommandGatewayFactory factory = new CommandGatewayFactory(commandBus, retryScheduler, dispatchInterceptors);
        commandCallbacks.forEach(factory::registerCommandCallback);
        gateway = factory.createGateway(gatewayInterface);
    }

    /**
     * Sets the command bus on which the Gateway must dispatch commands. This property is required.
     *
     * @param commandBus the command bus on which the Gateway must dispatch commands
     */
    @Required
    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Sets the RetryScheduler that will be invoked when a command fails execution. If no scheduler is provided,
     * commands will report the failure immediately.
     *
     * @param retryScheduler the RetryScheduler that will be invoked when a command fails execution
     */
    public void setRetryScheduler(RetryScheduler retryScheduler) {
        this.retryScheduler = retryScheduler;
    }

    /**
     * Sets the interface that describes the gateway instance to describe. If no interface is provided, it defaults to
     * {@link CommandGateway}.
     *
     * @param gatewayInterface The interface describing the gateway
     * @throws IllegalArgumentException if the given {@code gatewayInterface} is {@code null} or not an
     * interface.
     */
    public void setGatewayInterface(Class<T> gatewayInterface) {
        Assert.notNull(gatewayInterface, () -> "The given gateway interface may not be null");
        Assert.isTrue(gatewayInterface.isInterface(), () -> "The given gateway interface must be an interface");
        this.gatewayInterface = gatewayInterface;
    }

    /**
     * Add an interceptor that should be invoked before a command is dispatched the the Command Bus.
     * <p/>
     * Note that interceptors added here are specific to this Gateway instance. Messages dispatched through other
     * gateways or directly to the command bus will not pass through these interceptors.
     *
     * @param messageDispatchInterceptor the interceptor that should be invoked before a command is dispatched the
     *                                   the Command Bus
     */
    public void addCommandDispatchInterceptor(
            MessageDispatchInterceptor<CommandMessage<?>> messageDispatchInterceptor) {
        this.dispatchInterceptors.add(messageDispatchInterceptor);
    }

    /**
     * Sets the interceptors that should be invoked before a command is dispatched the the Command Bus.
     * <p/>
     * Note that these interceptors will be specific to this Gateway instance. Messages dispatched through other
     * gateways or directly to the command bus will not pass through these interceptors.
     *
     * @param messageDispatchInterceptors the interceptors that should be invoked before a command is dispatched the
     *                                    the
     *                                    Command Bus
     */
    public void setCommandDispatchInterceptors(
            List<MessageDispatchInterceptor<CommandMessage<?>>> messageDispatchInterceptors) {
        this.dispatchInterceptors = messageDispatchInterceptors;
    }

    /**
     * Registers the {@code commandCallbacks}, which are invoked for each sent command, unless Axon is able to
     * detect
     * that the result of the command does not match the type accepted by that callback.
     * <p/>
     * Axon will check the signature of the onSuccess() method and only invoke the callback if the actual result of the
     * command is an instance of that type. If Axon is unable to detect the type, the callback is always invoked,
     * potentially causing {@link java.lang.ClassCastException}.
     *
     * @param commandCallbacks The callbacks to register
     */
    public void setCommandCallbacks(List<CommandCallback<?, ?>> commandCallbacks) {
        this.commandCallbacks = commandCallbacks;
    }
}

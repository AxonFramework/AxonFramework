/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.common.AxonConfigurationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * FactoryBean that creates a DefaultCommandGateway instance. In contrary to the DefaultCommandGateway itself, this
 * factory bean allows for setter injection, making it easier to configure in Spring application contexts.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandGatewayFactoryBean implements FactoryBean<CommandGateway>, InitializingBean {

    private CommandBus commandBus;
    private RetryScheduler retryScheduler;
    private List<CommandDispatchInterceptor> dispatchInterceptors = Collections.emptyList();

    @Override
    public CommandGateway getObject() throws Exception {
        return new DefaultCommandGateway(commandBus, retryScheduler, dispatchInterceptors);
    }

    @Override
    public Class<?> getObjectType() {
        return CommandGateway.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (commandBus == null) {
            throw new AxonConfigurationException("CommandBus may not be null");
        }
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
     * Sets the interceptors that should be invoked before a command is dispatched the the Command Bus.
     * <p/>
     * Note that these interceptors will be specific to this Gateway instance. Messages dispatched through other
     * gateways or directly to the command bus will not pass through these interceptors.
     *
     * @param commandDispatchInterceptors the interceptors that should be invoked before a command is dispatched the
     *                                    the
     *                                    Command Bus
     */
    public void setCommandDispatchInterceptors(CommandDispatchInterceptor... commandDispatchInterceptors) {
        setCommandDispatchInterceptors(asList(commandDispatchInterceptors));
    }

    /**
     * Sets the interceptors that should be invoked before a command is dispatched the the Command Bus.
     * <p/>
     * Note that these interceptors will be specific to this Gateway instance. Messages dispatched through other
     * gateways or directly to the command bus will not pass through these interceptors.
     *
     * @param commandDispatchInterceptors the interceptors that should be invoked before a command is dispatched the
     *                                    the
     *                                    Command Bus
     */
    public void setCommandDispatchInterceptors(List<CommandDispatchInterceptor> commandDispatchInterceptors) {
        this.dispatchInterceptors = commandDispatchInterceptors;
    }
}

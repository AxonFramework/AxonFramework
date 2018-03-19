/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * Parameter resolver factory that enables injection of {@link QueryUpdateEmitter} to query handlers.
 * <p>
 * Injection of correct {@link QueryUpdateEmitter} is achieved using
 * {@link QueryUpdateEmitterParameterResolverFactory#initialize(QueryUpdateEmitter)}.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class QueryUpdateEmitterParameterResolverFactory
        implements ParameterResolverFactory, ParameterResolver<QueryUpdateEmitter> {

    private static final String QUERY_UPDATE_EMITTER_KEY = QueryUpdateEmitter.class.getName();

    /**
     * Attaches provided {@code queryUpdateEmitter} to the current unit of work. If there is no unit of work started,
     * {@link IllegalStateException} will be thrown.
     *
     * @param queryUpdateEmitter the query update emitter to be injected to the query handler
     */
    public static void initialize(QueryUpdateEmitter queryUpdateEmitter) {
        Assert.state(CurrentUnitOfWork.isStarted(), () -> "An active Unit of Work is required for injecting emitter");
        CurrentUnitOfWork.get().getOrComputeResource(QUERY_UPDATE_EMITTER_KEY, key -> queryUpdateEmitter);
    }

    @Override
    public QueryUpdateEmitter resolveParameterValue(Message<?> message) {
        return CurrentUnitOfWork.map(uow -> (QueryUpdateEmitter) uow.getResource(QUERY_UPDATE_EMITTER_KEY))
                                .orElseThrow(() -> new IllegalStateException(
                                        "QueryUpdateEmitter should have been injected"));
    }

    @Override
    public boolean matches(Message<?> message) {
        return message instanceof QueryMessage;
    }

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (QueryUpdateEmitter.class.equals(parameters[parameterIndex].getType())) {
            return this;
        }
        return null;
    }
}

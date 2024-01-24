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
package org.axonframework.queryhandling.annotation;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Adapter that turns any {@link QueryHandler @QueryHandler} annotated bean into a {@link MessageHandler}
 * implementation. Each annotated method is subscribed as a QueryHandler at the {@link QueryBus} for the query type
 * specified by the parameter/return type of that method.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class AnnotationQueryHandlerAdapter<T> implements QueryHandlerAdapter, MessageHandler<QueryMessage<?, ?>, Object> {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;

    /**
     * Initializes the adapter, forwarding call to the given {@code target}.
     *
     * @param target the instance with {@link QueryHandler} annotated methods
     */
    public AnnotationQueryHandlerAdapter(T target) {
        this(target, ClasspathParameterResolverFactory.forClass(target.getClass()));
    }

    /**
     * Initializes the adapter, forwarding call to the given {@code target}, resolving parameters using the given {@code
     * parameterResolverFactory}.
     *
     * @param target                   the instance with {@link QueryHandler} annotated methods
     * @param parameterResolverFactory the parameter resolver factory to resolve handler parameters with
     */
    public AnnotationQueryHandlerAdapter(T target, ParameterResolverFactory parameterResolverFactory) {
        this(target,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(target.getClass()));
    }

    /**
     * Initializes the adapter, forwarding call to the given {@code target}, resolving parameters using the given {@code
     * parameterResolverFactory} and creating handlers using {@code handlerDefinition}.
     *
     * @param target                   the instance with {@link QueryHandler} annotated methods
     * @param parameterResolverFactory the parameter resolver factory to resolve handler parameters with
     * @param handlerDefinition        the handler definition used to create concrete handlers
     */
    @SuppressWarnings("unchecked")
    public AnnotationQueryHandlerAdapter(T target,
                                         ParameterResolverFactory parameterResolverFactory,
                                         HandlerDefinition handlerDefinition) {
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) target.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);
        this.target = target;
    }

    @Override
    public Registration subscribe(@Nonnull QueryBus queryBus) {
        Collection<Registration> registrations = model.getHandlers(target.getClass())
                                                      .map(handler -> handler.unwrap(QueryHandlingMember.class))
                                                      .filter(Optional::isPresent)
                                                      .map(Optional::get)
                                                      .map(queryHandler -> queryBus.subscribe(
                                                              queryHandler.getQueryName(),
                                                              queryHandler.getResultType(),
                                                              this
                                                      ))
                                                      .collect(Collectors.toList());

        return () -> registrations.stream()
                                  .map(Registration::cancel)
                                  .reduce(Boolean::logicalOr)
                                  .orElse(false);
    }

    @Override
    public Object handleSync(QueryMessage<?, ?> message) throws Exception {
        MessageHandlingMember<? super T> handler =
                model.getHandlers(target.getClass())
                     .filter(m -> m.canHandle(message, null))
                     .findFirst()
                     .orElseThrow(() -> new NoHandlerForQueryException(message));

        return model.chainedInterceptor(target.getClass())
                    .handleSync(message, target, handler);
    }

    @Override
    public boolean canHandle(QueryMessage<?, ?> message) {
        return model.getHandlers(target.getClass())
                    .anyMatch(handlingMember -> handlingMember.canHandle(message, null));
    }
}

/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.axonframework.queryhandling.annotations;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotations.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotations.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotations.HandlerDefinition;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.interceptors.annotations.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryHandlingComponent;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns classes with {@link QueryHandler} annotated methods into a {@link QueryHandlingComponent}.
 * <p>
 * Each annotated method is subscribed as a {@link org.axonframework.queryhandling.QueryHandler} at the
 * {@link QueryHandlingComponent} for the query name and response type specified by that method.
 *
 * @param <T> The target type of this query handling component.
 * @author Marc Gathier
 * @author Steven van Beelen
 * @since 3.1.0
 */
public class AnnotatedQueryHandlingComponent<T> implements QueryHandlingComponent {

    private final SimpleQueryHandlingComponent handlingComponent;
    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final MessageTypeResolver messageTypeResolver;
    private final MessageConverter converter;

    /**
     * Wraps the given {@code annotatedQueryHandler}, allowing it to be subscribed to a {@link QueryBus} as a
     * {@link QueryHandlingComponent}.
     *
     * @param annotatedQueryHandler The object containing the {@link QueryHandler} annotated methods.
     * @param converter             The converter to use for converting the payload of the query to the type expected by
     *                              the handler method.
     */
    public AnnotatedQueryHandlingComponent(@Nonnull T annotatedQueryHandler,
                                           @Nonnull MessageConverter converter) {
        this(annotatedQueryHandler,
             ClasspathParameterResolverFactory.forClass(annotatedQueryHandler.getClass()),
             converter);
    }

    /**
     * Wraps the given {@code annotatedQueryHandler}, allowing it to be subscribed to a {@link QueryBus} as a
     * {@link QueryHandlingComponent}.
     *
     * @param annotatedQueryHandler    The object containing the {@link QueryHandler} annotated methods.
     * @param parameterResolverFactory The parameter resolver factory to resolve handler parameters with.
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                 expected by the handler method.
     */
    public AnnotatedQueryHandlingComponent(@Nonnull T annotatedQueryHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull MessageConverter converter) {
        this(annotatedQueryHandler,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedQueryHandler.getClass()),
             new ClassBasedMessageTypeResolver(),
             converter);
    }

    /**
     * Wraps the given {@code annotatedQueryHandler}, allowing it to be subscribed to a {@link QueryBus} as a
     * {@link QueryHandlingComponent}.
     *
     * @param annotatedQueryHandler    The object containing the {@link QueryHandler} annotated methods.
     * @param parameterResolverFactory The parameter resolver factory to resolve handler parameters with.
     * @param handlerDefinition        The handler definition used to create concrete handlers.
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the {@link QualifiedName names} for
     *                                 {@link QueryMessage QueryMessage} and
     *                                 {@link QueryResponseMessage QueryResponseMessages}.
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                 expected by the handler method.
     */
    public AnnotatedQueryHandlingComponent(@Nonnull T annotatedQueryHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull HandlerDefinition handlerDefinition,
                                           @Nonnull MessageTypeResolver messageTypeResolver,
                                           @Nonnull MessageConverter converter) {
        this.handlingComponent = SimpleQueryHandlingComponent.create(
                "AnnotatedQueryHandlingComponent[%s]".formatted(annotatedQueryHandler.getClass().getName())
        );
        this.target = requireNonNull(annotatedQueryHandler, "The Annotated Query Handler may not be null.");
        //noinspection unchecked
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) annotatedQueryHandler.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null.");
        this.converter = requireNonNull(converter, "The Converter may not be null.");

        initializeHandlersBasedOnModel();
    }

    private void initializeHandlersBasedOnModel() {
        //noinspection OptionalGetWithoutIsPresent
        model.getAllHandlers().forEach(
                (modelClass, handlers) ->
                        handlers.stream()
                                .filter(handler -> handler.canHandleMessageType(QueryMessage.class))
                                .filter(handler -> handler.unwrap(QueryHandlingMember.class).isPresent())
                                .map(handler -> handler.unwrap(QueryHandlingMember.class).get())
                                .forEach(this::registerHandler)
        );
    }

    private void registerHandler(QueryHandlingMember<? super T> handler) {
        Type resultType = handler.resultType();
        QualifiedName queryName = new QualifiedName(handler.queryName());
        MessageType responseType = messageTypeResolver.resolve((Class<?>) resultType)
                                                      .orElseGet(() -> new MessageType((Class<?>) resultType));
        handlingComponent.subscribe(queryName, responseType.qualifiedName(), constructQueryHandlerFor(handler));
    }

    private org.axonframework.queryhandling.QueryHandler constructQueryHandlerFor(
            QueryHandlingMember<? super T> handler
    ) {
        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        return (query, context) -> {
            MessageStream<QueryResponseMessage> resultStream =
                    interceptorChain.handle(
                                            query.withConvertedPayload(handler.payloadType(), converter),
                                            context,
                                            target,
                                            handler
                                    )
                                    .mapMessage(this::asQueryResponseMessage);
            Optional<Throwable> handlingException = resultStream.error();
            if (handlingException.isPresent() && !(handlingException.get() instanceof QueryExecutionException)) {
                return MessageStream.failed(new QueryExecutionException(
                        "Handling query with identifier [" + query.identifier() + "] failed.",
                        handlingException.get()
                ));
            }
            return resultStream;
        };
    }

    private QueryResponseMessage asQueryResponseMessage(@Nonnull Message queryResponse) {
        return queryResponse instanceof QueryResponseMessage
                ? (QueryResponseMessage) queryResponse
                : new GenericQueryResponseMessage(queryResponse);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> handle(@Nonnull QueryMessage query,
                                                      @Nonnull ProcessingContext context) {
        return handlingComponent.handle(query, context);
    }

    @Override
    public Set<QueryHandlerName> supportedQueries() {
        return Set.copyOf(handlingComponent.supportedQueries());
    }
}

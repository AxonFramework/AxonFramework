/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of an {@link EventHandlerInvoker} that forwards events to a list of registered
 * {@link EventMessageHandler}.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class SimpleEventHandlerInvoker implements EventHandlerInvoker {

    private final List<?> eventHandlers;
    private final List<EventMessageHandler> wrappedEventHandlers;
    private final ListenerInvocationErrorHandler listenerInvocationErrorHandler;
    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;

    /**
     * Instantiate a {@link SimpleEventHandlerInvoker} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that at least one {@link EventMessageHandler} is provided, and will throw an
     * {@link AxonConfigurationException} if this is not the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleEventHandlerInvoker} instance
     */
    protected SimpleEventHandlerInvoker(Builder builder) {
        builder.validate();
        this.eventHandlers = builder.eventHandlers;
        this.wrappedEventHandlers =
                eventHandlers.stream()
                             .map(handler -> handler instanceof EventMessageHandler
                                     ? (EventMessageHandler) handler
                                     : builder.wrapEventMessageHandler(handler))
                             .collect(Collectors.toCollection(ArrayList::new));
        this.sequencingPolicy = builder.sequencingPolicy;
        this.listenerInvocationErrorHandler = builder.listenerInvocationErrorHandler;
    }

    /**
     * Checks if a {@link List} has been passed as first parameter. It is a common 'mistake', which is detected and
     * fixed here.
     *
     * @param eventHandlers the event handlers to check whether it contains a {@link List}
     * @return a {@link List} of events handlers
     */
    private static List<?> detectList(Object[] eventHandlers) {
        return eventHandlers.length == 1 && (eventHandlers[0] instanceof List)
                ? (List<?>) eventHandlers[0]
                : asList(eventHandlers);
    }

    /**
     * Instantiate a Builder to be able to create a {@link SimpleEventHandlerInvoker}.
     * <p>
     * The {@link ListenerInvocationErrorHandler} is defaulted to a {@link LoggingErrorHandler} and the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}. Providing at least one Event Handler is a
     * <b>hard requirement</b> and thus should be accounted for.
     *
     * @return a Builder to be able to create a {@link SimpleEventHandlerInvoker}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the list of Event Handler delegates. This delegates are the end point of event handling.
     *
     * @return the list of Event Handler delegates
     */
    public List<?> eventHandlers() {
        return Collections.unmodifiableList(eventHandlers);
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        if (sequencingPolicyMatchesSegment(message, segment)) {
            for (EventMessageHandler handler : wrappedEventHandlers) {
                try {
                    handler.handle(message);
                } catch (Exception e) {
                    listenerInvocationErrorHandler.onError(e, message, handler);
                }
            }
        }
    }

    @Override
    public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
        return hasHandler(eventMessage) && sequencingPolicyMatchesSegment(eventMessage, segment);
    }

    @Override
    public boolean canHandleType(Class<?> payloadType) {
        return wrappedEventHandlers.stream().anyMatch(eh -> eh.canHandleType(payloadType));
    }

    private boolean hasHandler(EventMessage<?> eventMessage) {
        for (EventMessageHandler eventHandler : wrappedEventHandlers) {
            if (eventHandler.canHandle(eventMessage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean supportsReset() {
        for (EventMessageHandler eventHandler : wrappedEventHandlers) {
            if (!eventHandler.supportsReset()) {
                return false;
            }
        }
        return true;
    }

    private boolean sequencingPolicyMatchesSegment(EventMessage<?> message, Segment segment) {
        return segment.matches(Objects.hashCode(getOrDefault(
                sequencingPolicy.getSequenceIdentifierFor(message),
                message::getIdentifier)
        ));
    }

    @Override
    public void performReset() {
        performReset(null);
    }

    @Override
    public <R> void performReset(R resetContext) {
        for (EventMessageHandler eventHandler : wrappedEventHandlers) {
            eventHandler.prepareReset(resetContext);
        }
    }

    public ListenerInvocationErrorHandler getListenerInvocationErrorHandler() {
        return listenerInvocationErrorHandler;
    }

    public SequencingPolicy<? super EventMessage<?>> getSequencingPolicy() {
        return sequencingPolicy;
    }

    /**
     * Builder class to instantiate a {@link SimpleEventHandlerInvoker}.
     * <p>
     * The {@link ListenerInvocationErrorHandler} is defaulted to a {@link LoggingErrorHandler} and the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}. Providing at least one Event Handler is a
     * <b>hard requirement</b> and thus should be accounted for.
     */
    public static class Builder {

        private List<?> eventHandlers;
        private ParameterResolverFactory parameterResolverFactory;
        private HandlerDefinition handlerDefinition;
        private ListenerInvocationErrorHandler listenerInvocationErrorHandler = new LoggingErrorHandler();
        private SequencingPolicy<? super EventMessage<?>> sequencingPolicy = SequentialPerAggregatePolicy.instance();

        /**
         * Sets the {@code eventHandlers} this {@link EventHandlerInvoker} will forward all its events to. If an event
         * handler is assignable to {@link EventMessageHandler} it will registered as is. If not, it will be wrapped by
         * a new {@link AnnotationEventHandlerAdapter}.
         *
         * @param eventHandlers an array of {@link Object}s which can handle events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventHandlers(Object... eventHandlers) {
            return eventHandlers(detectList(eventHandlers));
        }

        /**
         * Sets the {@code eventHandlers} this {@link EventHandlerInvoker} will forward all its events to. If an event
         * handler is assignable to {@link EventMessageHandler} it will registered as is. If not, it will be wrapped by
         * a new {@link AnnotationEventHandlerAdapter}.
         *
         * @param eventHandlers a {@link List} of {@link Object}s which can handle events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventHandlers(List<?> eventHandlers) {
            assertThat(eventHandlers, list -> !list.isEmpty(), "At least one EventMessageHandler should be provided");
            this.eventHandlers = eventHandlers;
            return this;
        }

        /**
         * Sets the {@link ParameterResolverFactory} used to resolve parameter values for annotated handlers in the
         * {@link AnnotationEventHandlerAdapter} this {@link EventHandlerInvoker} instantiates. This invoker will only
         * instantiate a new {@link EventMessageHandler} if a given Event Handler (through
         * {@link #eventHandlers(Object...)} or {@link #eventHandlers(List)}) is not assignable to EventMessageHandler.
         *
         * @param parameterResolverFactory the {@link ParameterResolverFactory} used to resolve parameter values for
         *                                 instantiated {@link AnnotationEventHandlerAdapter}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            assertNonNull(parameterResolverFactory, "ParameterResolverFactory may not be null");
            this.parameterResolverFactory = parameterResolverFactory;
            return this;
        }

        /**
         * Sets the {@link HandlerDefinition} used to create concrete handlers in the annotated handlers in the
         * {@link AnnotationEventHandlerAdapter} this {@link EventHandlerInvoker} instantiates. This invoker will only
         * instantiate a new {@link EventMessageHandler} if a given Event Handler
         * (through {@link #eventHandlers(Object...)} or {@link #eventHandlers(List)}) is not assignable to
         * EventMessageHandler.
         *
         * @param handlerDefinition the {@link HandlerDefinition} used to create concrete handlers in the instantiated
         *                          {@link AnnotationEventHandlerAdapter}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder handlerDefinition(HandlerDefinition handlerDefinition) {
            assertNonNull(handlerDefinition, "HandlerDefinition may not be null");
            this.handlerDefinition = handlerDefinition;
            return this;
        }

        /**
         * Sets the {@link ListenerInvocationErrorHandler} which deals with any {@link Exception}s being thrown by the
         * {@link EventListener}s. Defaults to a {@link LoggingErrorHandler}.
         *
         * @param listenerInvocationErrorHandler a {@link ListenerInvocationErrorHandler} which deals with any {@link
         *                                       Exception}s being thrown by the {@link EventListener}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder listenerInvocationErrorHandler(ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
            assertNonNull(listenerInvocationErrorHandler, "ListenerInvocationErrorHandler may not be null");
            this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
            return this;
        }

        /**
         * Sets the {@link SequencingPolicy} in charge of deciding whether a given event should be handled (through
         * {@link SimpleEventHandlerInvoker#handle(EventMessage, Segment)}) by the given {@link Segment}. Used when this
         * {@link EventHandlerInvoker} is invoked for multiple Segments (i.e. using parallel processing). Defaults to a
         * {@link SequentialPerAggregatePolicy},
         *
         * @param sequencingPolicy a {@link SequencingPolicy} in charge of deciding whether a given event should be
         *                         handled by the given {@link Segment}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder sequencingPolicy(SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
            assertNonNull(sequencingPolicy, "{} may not be null");
            this.sequencingPolicy = sequencingPolicy;
            return this;
        }

        /**
         * Initializes a {@link SimpleEventHandlerInvoker} as specified through this Builder.
         *
         * @return a {@link SimpleEventHandlerInvoker} as specified through this Builder
         */
        public SimpleEventHandlerInvoker build() {
            return new SimpleEventHandlerInvoker(this);
        }

        /**
         * Wrap a given {@code eventHandler} in an {@link AnnotationEventHandlerAdapter} to allow this
         * {@link EventHandlerInvoker} to correctly pass {@link EventMessage}s to it. If a
         * {@link ParameterResolverFactory} or both a ParameterResolverFactory and {@link HandlerDefinition} are
         * present, one/both will be given to the AnnotationEventHandlerAdapter
         *
         * @param eventHandler an {@link Object} which will be wrapped in an {@link AnnotationEventHandlerAdapter}
         * @return an {@link AnnotationEventHandlerAdapter} which the given {@code eventHandler} will be wrapped in
         */
        public AnnotationEventHandlerAdapter wrapEventMessageHandler(Object eventHandler) {
            if (parameterResolverFactory == null && handlerDefinition == null) {
                return new AnnotationEventHandlerAdapter(eventHandler);
            } else if (parameterResolverFactory != null && handlerDefinition == null) {
                return new AnnotationEventHandlerAdapter(eventHandler, parameterResolverFactory);
            } else {
                return new AnnotationEventHandlerAdapter(eventHandler, parameterResolverFactory, handlerDefinition);
            }
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertThat(eventHandlers, list -> list != null && !list.isEmpty(), "At least one EventMessageHandler should be provided");
        }
    }
}

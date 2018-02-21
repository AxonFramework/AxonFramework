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

package org.axonframework.eventhandling;

import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of an {@link EventHandlerInvoker} that forwards events to a list of registered {@link EventListener
 * EventListeners}.
 *
 * @author Rene de Waele
 */
public class SimpleEventHandlerInvoker implements EventHandlerInvoker {

    private final List<EventListener> eventListeners;
    private final ListenerInvocationErrorHandler listenerInvocationErrorHandler;
    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;

    /**
     * Initializes a {@link SimpleEventHandlerInvoker} containing one or more {@code eventListeners}. If an event
     * listener is assignable to {@link EventListener} it will registered as is. If not, it will be wrapped by a new
     * {@link AnnotationEventListenerAdapter}.
     * <p>
     * Events handled by the invoker will be passed to all the given {@code eventListeners}. If an exception is
     * triggered during event handling it will be logged using a {@link LoggingErrorHandler} but otherwise
     * ignored.
     * <p>
     * When this invoker is invoked for multiple Segments (i.e. using parallel processing), events from the same
     * Aggregate are guaranteed to be processed in sequence (see {@link SequentialPerAggregatePolicy}).
     *
     * @param eventListeners one or more event listeners to register with this invoker
     */
    public SimpleEventHandlerInvoker(Object... eventListeners) {
        this(detectList(eventListeners), new LoggingErrorHandler());
    }

    /**
     * Initializes a {@link SimpleEventHandlerInvoker} containing the given list of {@code eventListeners}. If an event
     * listener is assignable to {@link EventListener} it will registered as is. If not, it will be wrapped by a new
     * {@link AnnotationEventListenerAdapter}.
     * <p>
     * Events handled by the invoker will be passed to all the given {@code eventListeners}. If an exception is
     * triggered during event handling it will be handled by the given {@code listenerErrorHandler}.
     * <p>
     * When this invoker is invoked for multiple Segments (i.e. using parallel processing), events from the same
     * Aggregate are guaranteed to be processed in sequence (see {@link SequentialPerAggregatePolicy}).
     *
     * @param eventListeners                 list of event listeners to register with this invoker
     * @param listenerInvocationErrorHandler error handler that handles exceptions during processing
     */
    public SimpleEventHandlerInvoker(List<?> eventListeners,
                                     ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        this(eventListeners, listenerInvocationErrorHandler, new SequentialPerAggregatePolicy());
    }

    /**
     * Initialize the EventHandlerInvoker to invoke the given {@code eventListeners}, using the given
     * {@code listenerInvocationErrorHandler} when an error occurs invoking these handlers and the given
     * {@code sequencingPolicy} to describe the expected sequencing of event messages
     *
     * @param eventListeners                 The listeners to invoke
     * @param listenerInvocationErrorHandler The error handler to invoke when an error occurs
     * @param sequencingPolicy               The policy describing the expectations of sequential processing
     */
    public SimpleEventHandlerInvoker(List<?> eventListeners,
                                     ListenerInvocationErrorHandler listenerInvocationErrorHandler,
                                     SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this.eventListeners = new ArrayList<>(eventListeners.stream()
                                                            .map(listener -> listener instanceof EventListener ?
                                                                    (EventListener) listener :
                                                                    new AnnotationEventListenerAdapter(listener))
                                                            .collect(toList()));
        this.sequencingPolicy = sequencingPolicy;
        this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
    }

    /**
     * Initializes a {@link SimpleEventHandlerInvoker} containing the given list of {@code eventListeners}. If an event
     * listener is assignable to {@link EventListener} it will registered as is. If not, it will be wrapped by a new
     * {@link AnnotationEventListenerAdapter}.
     * <p>
     * Events handled by the invoker will be passed to all the given {@code eventListeners}. If an exception is
     * triggered during event handling it will be handled by the given {@code listenerErrorHandler}.
     *
     * @param eventListeners                 list of event listeners to register with this invoker
     * @param parameterResolverFactory       The parameter resolver factory to resolve parameters of the Event Handler methods with
     * @param listenerInvocationErrorHandler error handler that handles exceptions during processing
     */
    public SimpleEventHandlerInvoker(List<?> eventListeners,
                                     ParameterResolverFactory parameterResolverFactory,
                                     ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        this(eventListeners, parameterResolverFactory, listenerInvocationErrorHandler, new SequentialPerAggregatePolicy());
    }

    /**
     * Initializes a {@link SimpleEventHandlerInvoker} containing the given list of {@code eventListeners}. If an event
     * listener is assignable to {@link EventListener} it will registered as is. If not, it will be wrapped by a new
     * {@link AnnotationEventListenerAdapter}.
     * <p>
     * Events handled by the invoker will be passed to all the given {@code eventListeners}. If an exception is
     * triggered during event handling it will be handled by the given {@code listenerErrorHandler}.
     *
     * @param eventListeners                 list of event listeners to register with this invoker
     * @param parameterResolverFactory       The parameter resolver factory to resolve parameters of the Event Handler methods with
     * @param listenerInvocationErrorHandler error handler that handles exceptions during processing
     * @param sequencingPolicy               The policy describing the expectations of sequential processing
     */
    public SimpleEventHandlerInvoker(List<?> eventListeners,
                                     ParameterResolverFactory parameterResolverFactory,
                                     ListenerInvocationErrorHandler listenerInvocationErrorHandler,
                                     SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this.eventListeners = new ArrayList<>(eventListeners.stream()
                                                            .map(listener -> listener instanceof EventListener ?
                                                                    (EventListener) listener :
                                                                    new AnnotationEventListenerAdapter(listener, parameterResolverFactory))
                                                            .collect(toList()));
        this.sequencingPolicy = sequencingPolicy;
        this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
    }

    /**
     * Checks if a List has been passed as first parameter. It is a common 'mistake', which is detected and fixed here.
     *
     * @param eventListeners The event listeners to check for a list
     * @return a list of events listeners
     */
    private static List<?> detectList(Object[] eventListeners) {
        return eventListeners.length == 1 && (eventListeners[0] instanceof List) ? (List<?>) eventListeners[0] :
                Arrays.asList(eventListeners);
    }


    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        for (EventListener listener : eventListeners) {
            try {
                listener.handle(message);
            } catch (Exception e) {
                listenerInvocationErrorHandler.onError(e, message, listener);
            }
        }
    }

    @Override
    public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
        return hasHandler(eventMessage)
                && segment.matches(Objects.hashCode(getOrDefault(sequencingPolicy.getSequenceIdentifierFor(eventMessage),
                                                                 eventMessage::getIdentifier)));
    }

    private boolean hasHandler(EventMessage<?> eventMessage) {
        for (EventListener eventListener : eventListeners) {
            if (eventListener.canHandle(eventMessage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean supportsReset() {
        for (EventListener eventListener : eventListeners) {
            if (!eventListener.supportsReset()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void performReset() {
        for (EventListener eventListener : eventListeners) {
            eventListener.prepareReset();
        }
    }
}

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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.Subscribable;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListenerProxy;
import org.axonframework.eventhandling.replay.ReplayAware;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Adapter that turns any bean with {@link EventHandler} annotated methods into an {@link
 * org.axonframework.eventhandling.EventListener}.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.EventListener
 * @since 0.1
 */
public class AnnotationEventListenerAdapter implements Subscribable, EventListenerProxy, ReplayAware {

    private final MessageHandlerInvoker invoker;
    private final EventBus eventBus;
    private final ReplayAware replayAware;
    private final Class<?> listenerType;

    /**
     * Subscribe the given <code>annotatedEventListener</code> to the given <code>eventBus</code>.
     *
     * @param annotatedEventListener The annotated event listener
     * @param eventBus               The event bus to subscribe to
     * @return an AnnotationEventListenerAdapter that wraps the listener. Can be used to unsubscribe.
     */
    public static AnnotationEventListenerAdapter subscribe(Object annotatedEventListener, EventBus eventBus) {
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(annotatedEventListener);
        eventBus.subscribe(adapter);
        return adapter;
    }

    /**
     * Wraps the given <code>annotatedEventListener</code>, allowing it to be subscribed to an Event Bus.
     *
     * @param annotatedEventListener the annotated event listener
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener) {
        this(annotatedEventListener, ClasspathParameterResolverFactory.forClass(annotatedEventListener.getClass()));
    }

    /**
     * Wraps the given <code>annotatedEventListener</code>, allowing it to be subscribed to an Event Bus. The given
     * <code>parameterResolverFactory</code> is used to resolve parameter values for handler methods.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener,
                                          ParameterResolverFactory parameterResolverFactory) {
        this.invoker = new MessageHandlerInvoker(annotatedEventListener, parameterResolverFactory, false,
                                                 AnnotatedEventHandlerDefinition.INSTANCE);
        this.listenerType = annotatedEventListener.getClass();
        if (annotatedEventListener instanceof ReplayAware) {
            this.replayAware = (ReplayAware) annotatedEventListener;
        } else {
            // as soon as annotations are supported, their handlers should come here...
            this.replayAware = new NoOpReplayAware();
        }
        this.eventBus = null;
    }

    /**
     * Initialize the AnnotationEventListenerAdapter for the given <code>annotatedEventListener</code>. When the
     * adapter
     * subscribes, it will subscribe to the given event bus.
     *
     * @param annotatedEventListener the event listener
     * @param eventBus               the event bus to register the event listener to
     * @deprecated Use {@link #AnnotationEventListenerAdapter(Object)} and subscribe the listener to the event bus
     * using {@link EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @Deprecated
    public AnnotationEventListenerAdapter(Object annotatedEventListener, EventBus eventBus) {
        ParameterResolverFactory factory = ClasspathParameterResolverFactory.forClass(annotatedEventListener
                                                                                              .getClass());
        this.invoker = new MessageHandlerInvoker(annotatedEventListener, factory, false,
                                                 AnnotatedEventHandlerDefinition.INSTANCE);
        this.listenerType = annotatedEventListener.getClass();
        this.eventBus = eventBus;
        if (annotatedEventListener instanceof ReplayAware) {
            this.replayAware = (ReplayAware) annotatedEventListener;
        } else {
            // as soon as annotations are supported, their handlers should come here...
            this.replayAware = new NoOpReplayAware();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(EventMessage event) {
        invoker.invokeHandlerMethod(event);
    }

    /**
     * Unsubscribe the EventListener with the configured EventBus.
     *
     * @deprecated Use {@link EventBus#unsubscribe(org.axonframework.eventhandling.EventListener)} and
     * pass this adapter instance to unsubscribe it.
     */
    @Override
    @PreDestroy
    @Deprecated
    public void unsubscribe() {
        if (eventBus != null) {
            eventBus.unsubscribe(this);
        }
    }

    /**
     * Subscribe the EventListener with the configured EventBus.
     * <p/>
     *
     * @deprecated Use {@link EventBus#subscribe(org.axonframework.eventhandling.EventListener)} and
     * pass this adapter instance to subscribe it.
     */
    @Override
    @PostConstruct
    @Deprecated
    public void subscribe() {
        if (eventBus != null) {
            eventBus.subscribe(this);
        }
    }


    @Override
    public Class<?> getTargetType() {
        return listenerType;
    }

    @Override
    public void beforeReplay() {
        replayAware.beforeReplay();
    }

    @Override
    public void afterReplay() {
        replayAware.afterReplay();
    }

    @Override
    public void onReplayFailed(Throwable cause) {
        replayAware.onReplayFailed(cause);
    }

    private static final class NoOpReplayAware implements ReplayAware {

        @Override
        public void beforeReplay() {
        }

        @Override
        public void afterReplay() {
        }

        @Override
        public void onReplayFailed(Throwable cause) {
        }
    }
}

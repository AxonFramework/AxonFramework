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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.Subscribable;
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

    private final AnnotationEventHandlerInvoker invoker;
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
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(annotatedEventListener, eventBus);
        adapter.subscribe();
        return adapter;
    }

    /**
     * Initialize the AnnotationEventListenerAdapter for the given <code>annotatedEventListener</code>. When the
     * adapter
     * subscribes, it will subscribe to the given event bus.
     *
     * @param annotatedEventListener the event listener
     * @param eventBus               the event bus to register the event listener to
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener, EventBus eventBus) {
        this.invoker = new AnnotationEventHandlerInvoker(annotatedEventListener);
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
        invoker.invokeEventHandlerMethod(event);
    }

    /**
     * Unsubscribe the EventListener with the configured EventBus.
     */
    @Override
    @PreDestroy
    public void unsubscribe() {
        eventBus.unsubscribe(this);
    }

    /**
     * Subscribe the EventListener with the configured EventBus.
     */
    @Override
    @PostConstruct
    public void subscribe() {
        eventBus.subscribe(this);
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

    private static final class NoOpReplayAware implements ReplayAware {

        @Override
        public void beforeReplay() {
        }

        @Override
        public void afterReplay() {
        }
    }
}

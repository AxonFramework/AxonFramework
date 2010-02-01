/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler.annotation;

import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventSequencingPolicy;
import org.axonframework.core.eventhandler.SequentialPolicy;
import org.axonframework.core.eventhandler.TransactionAware;
import org.axonframework.core.eventhandler.TransactionStatus;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Adapter that turns any bean with {@link org.axonframework.core.eventhandler.annotation.EventHandler} annotated
 * methods into an {@link org.axonframework.core.eventhandler.EventListener}.
 * <p/>
 * Optionally, this adapter may be configured with an {@link EventBus} at which the adapter should register for events.
 * If none is configured, one is autowired (requiring that exactly one {@link EventBus} is present in the
 * ApplicationContext.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class AnnotationEventListenerAdapter
        implements TransactionAware, org.axonframework.core.eventhandler.EventListener {

    private volatile EventBus eventBus;

    private final Object target;
    private final AnnotationEventHandlerInvoker eventHandlerInvoker;
    private final EventSequencingPolicy eventSequencingPolicy;
    private final TransactionAware transactionListener;

    /**
     * Initialize the AnnotationEventListenerAdapter for the given <code>annotatedEventListener</code>.
     *
     * @param annotatedEventListener the event listener
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener) {
        eventHandlerInvoker = new AnnotationEventHandlerInvoker(annotatedEventListener);
        eventSequencingPolicy = getSequencingPolicyFor(annotatedEventListener);
        this.target = annotatedEventListener;
        if (target instanceof TransactionAware) {
            transactionListener = (TransactionAware) target;
        } else {
            transactionListener = new AnnotatedTransactionAware(eventHandlerInvoker);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canHandle(Class<? extends Event> eventType) {
        return eventHandlerInvoker.hasHandlerFor(eventType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(Event event) {
        eventHandlerInvoker.invokeEventHandlerMethod(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventSequencingPolicy getEventSequencingPolicy() {
        return eventSequencingPolicy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeTransaction(TransactionStatus transactionStatus) {
        transactionListener.beforeTransaction(transactionStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterTransaction(TransactionStatus transactionStatus) {
        transactionListener.afterTransaction(transactionStatus);
    }

    /**
     * Returns the configuration of the event handler that would process the given <code>event</code>. Returns
     * <code>null</code> if no event handler is found for the given event.
     *
     * @param event the event for which to search configuration.
     * @return the annotation on the event handler method
     */
    public org.axonframework.core.eventhandler.annotation.EventHandler getConfigurationFor(Event event) {
        return eventHandlerInvoker.findEventHandlerConfiguration(event);
    }

    /**
     * {@inheritDoc}
     */
    @PreDestroy
    public void shutdown() {
        if (eventBus != null) {
            eventBus.unsubscribe(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @PostConstruct
    public void initialize() {
        if (eventBus != null) {
            eventBus.subscribe(this);
        }
    }

    private EventSequencingPolicy getSequencingPolicyFor(Object annotatedEventListener) {
        ConcurrentEventListener annotation = AnnotationUtils.findAnnotation(annotatedEventListener.getClass(),
                                                                            ConcurrentEventListener.class);
        if (annotation == null) {
            return new SequentialPolicy();
        }

        Class<? extends EventSequencingPolicy> policyClass = annotation.sequencingPolicyClass();
        try {
            return policyClass.newInstance();
        } catch (InstantiationException e) {
            throw new UnsupportedPolicyException(String.format(
                    "Could not initialize an instance of the given policy: [%s]. "
                            + "Does it have an accessible no-arg constructor?",
                    policyClass.getSimpleName()), e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedPolicyException(String.format(
                    "Could not initialize an instance of the given policy: [%s]. "
                            + "Is the no-arg constructor accessible?",
                    policyClass.getSimpleName()), e);
        }
    }

    /**
     * Returns the event listener to which events are forwarded
     *
     * @return the targeted event listener
     */
    public Object getTarget() {
        return target;
    }

    /**
     * Sets the event bus that this adapter should subscribe to.
     *
     * @param eventBus the EventBus to subscribe to
     * @see #initialize()
     * @see #shutdown()
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    private static class AnnotatedTransactionAware implements TransactionAware {

        private final AnnotationEventHandlerInvoker eventHandlerInvoker;

        /**
         * Initialize the Annotated Transaction Aware adapter for the given event handler invoker.
         *
         * @param eventHandlerInvoker the invoker to delegate transaction handling to
         */
        public AnnotatedTransactionAware(AnnotationEventHandlerInvoker eventHandlerInvoker) {
            this.eventHandlerInvoker = eventHandlerInvoker;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void beforeTransaction(TransactionStatus transactionStatus) {
            eventHandlerInvoker.invokeBeforeTransaction(transactionStatus);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterTransaction(TransactionStatus transactionStatus) {
            eventHandlerInvoker.invokeAfterTransaction(transactionStatus);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "AnnotationEventListenerAdapter(" + target.getClass().getSimpleName() + ")";
    }
}

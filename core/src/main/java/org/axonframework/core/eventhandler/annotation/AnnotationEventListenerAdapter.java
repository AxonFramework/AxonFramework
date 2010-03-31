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
import org.axonframework.core.eventhandler.AsynchronousEventHandlerWrapper;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventListener;
import org.axonframework.core.eventhandler.EventSequencingPolicy;
import org.axonframework.core.eventhandler.SequentialPolicy;
import org.axonframework.core.eventhandler.TransactionManager;
import org.axonframework.core.eventhandler.TransactionStatus;
import org.axonframework.core.util.annotation.AnnotatedHandlerAdapter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executor;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

/**
 * Adapter that turns any bean with {@link org.axonframework.core.eventhandler.annotation.EventHandler} annotated
 * methods into an {@link org.axonframework.core.eventhandler.EventListener}.
 * <p/>
 * If the event listener has the {@link @AsynchronousEventListener} annotation, it is also configured to handle events
 * asynchronously.
 * <p/>
 *
 * @author Allard Buijze
 * @see EventListener
 * @see org.axonframework.core.eventhandler.AsynchronousEventHandlerWrapper
 * @since 0.1
 */
public class AnnotationEventListenerAdapter implements AnnotatedHandlerAdapter, EventListener, TransactionManager {

    private final EventListener targetEventListener;
    private final Executor executor;
    private final TransactionManager transactionManager;
    private final EventBus eventBus;

    /**
     * Initialize the AnnotationEventListenerAdapter for the given <code>annotatedEventListener</code>. When the adapter
     * subscribes, it will subscribe to the given event bus.
     *
     * @param annotatedEventListener the event listener
     * @param eventBus               the event bus to register the event listener to
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener, EventBus eventBus) {
        this(annotatedEventListener, null, eventBus);
    }

    /**
     * Initialize the AnnotationEventListenerAdapter for the given <code>annotatedEventListener</code>. If the
     * <code>annotatedEventListener</code> is asynchronous (has the {@link AsynchronousEventListener}) annotation) then
     * the given executor is used to execute event processing.
     *
     * @param annotatedEventListener the event listener
     * @param executor               The executor to use when wiring an Asynchronous Event Listener.
     * @param eventBus               the event bus to register the event listener to
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener, Executor executor, EventBus eventBus) {
        EventListener adapter = new TargetEventListener(new AnnotationEventHandlerInvoker(annotatedEventListener));
        this.transactionManager = createTransactionManagerFor(annotatedEventListener);
        this.executor = executor;
        this.eventBus = eventBus;

        if (findAnnotation(annotatedEventListener.getClass(), AsynchronousEventListener.class) != null) {
            if (executor == null) {
                throw new IllegalArgumentException(
                        "The annotatedEventListener is Asynchronous, but no executor is provided.");
            }
            adapter = createAsynchronousWrapperForBean(annotatedEventListener, adapter);
        }
        this.targetEventListener = adapter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(Event event) {
        targetEventListener.handle(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeTransaction(TransactionStatus transactionStatus) {
        transactionManager.beforeTransaction(transactionStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterTransaction(TransactionStatus transactionStatus) {
        transactionManager.afterTransaction(transactionStatus);
    }

    /**
     * Unsubscribe the EventListener with the configured EventBus.
     */
    @PreDestroy
    public void unsubscribe() {
        eventBus.unsubscribe(this);
    }

    /**
     * Subscribe the EventListener with the configured EventBus.
     */
    @PostConstruct
    public void subscribe() {
        eventBus.subscribe(this);
    }

    private TransactionManager createTransactionManagerFor(Object bean) {
        TransactionManager tm;
        if (bean instanceof TransactionManager) {
            tm = (TransactionManager) bean;
        } else {
            tm = new AnnotationTransactionManager(bean);
        }
        return tm;
    }

    private AsynchronousEventHandlerWrapper createAsynchronousWrapperForBean(Object bean,
                                                                             EventListener adapter) {

        return new AsynchronousEventHandlerWrapper(adapter,
                                                   transactionManager,
                                                   getSequencingPolicyFor(bean),
                                                   executor);
    }

    private EventSequencingPolicy getSequencingPolicyFor(Object annotatedEventListener) {
        AsynchronousEventListener annotation = findAnnotation(annotatedEventListener.getClass(),
                                                              AsynchronousEventListener.class);
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

    private static final class TargetEventListener implements EventListener {

        private final AnnotationEventHandlerInvoker eventHandlerInvoker;

        public TargetEventListener(AnnotationEventHandlerInvoker eventHandlerInvoker) {
            this.eventHandlerInvoker = eventHandlerInvoker;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handle(Event event) {
            eventHandlerInvoker.invokeEventHandlerMethod(event);
        }
    }
}

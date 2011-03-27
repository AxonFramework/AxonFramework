/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.util.AxonConfigurationException;
import org.axonframework.util.FieldAccessibilityCallback;
import org.axonframework.util.ReflectionUtils;
import org.axonframework.util.Subscribable;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static java.security.AccessController.doPrivileged;
import static org.axonframework.util.ReflectionUtils.findAnnotation;

/**
 * Adapter that turns any bean with {@link EventHandler} annotated methods into an {@link
 * org.axonframework.eventhandling.EventListener}.
 * <p/>
 * If the event listener has the {@link AsynchronousEventListener} annotation, it is also configured to handle events
 * asynchronously. In that case, event processing is handed over to the given {@link java.util.concurrent.Executor}.
 *
 * @author Allard Buijze
 * @see EventListener
 * @see org.axonframework.eventhandling.AsynchronousEventHandlerWrapper
 * @since 0.1
 */
public class AnnotationEventListenerAdapter implements Subscribable, EventListenerProxy, TransactionManager {

    private final EventListener targetEventListener;
    private final Executor executor;
    private final TransactionManager transactionManager;
    private final EventBus eventBus;
    private final Object annotatedEventListener;

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
        this.annotatedEventListener = annotatedEventListener;
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

    private TransactionManager createTransactionManagerFor(Object bean) {
        TransactionManager tm;
        if (bean instanceof TransactionManager) {
            tm = (TransactionManager) bean;
        } else if (hasTransactionalMethods(bean)) {
            tm = new AnnotationTransactionManager(bean);
        } else {
            tm = findTransactionManagerInField(bean);
        }
        if (tm == null) {
            tm = new AnnotationTransactionManager(bean);
        }
        return tm;
    }

    private TransactionManager findTransactionManagerInField(Object bean) {
        TransactionManager tm = null;
        for (Field f : ReflectionUtils.fieldsOf(bean.getClass())) {
            try {
                if (f.isAnnotationPresent(org.axonframework.eventhandling.annotation.TransactionManager.class)) {
                    doPrivileged(new FieldAccessibilityCallback(f));

                    if (TransactionManager.class.isAssignableFrom(f.getType())) {
                        tm = (TransactionManager) f.get(bean);
                    } else {
                        tm = new AnnotationTransactionManager(f.get(bean));
                    }
                }
            } catch (IllegalAccessException e) {
                throw new AxonConfigurationException("Field should be accessible.", e);
            }
        }
        return tm;
    }

    private boolean hasTransactionalMethods(Object bean) {
//        HasTransactionMethodCallback hasTransactionMethodCallback = new HasTransactionMethodCallback();
        boolean found = false;
        for (Method method : ReflectionUtils.methodsOf(bean.getClass())) {
            if (method.isAnnotationPresent(BeforeTransaction.class)
                    || method.isAnnotationPresent(AfterTransaction.class)) {
                found = true;
            }
        }
        return found;
    }

    private AsynchronousEventHandlerWrapper createAsynchronousWrapperForBean(Object bean,
                                                                             EventListener adapter) {

        return new AsynchronousEventHandlerWrapper(adapter,
                                                   transactionManager,
                                                   getSequencingPolicyFor(bean),
                                                   executor);
    }

    private SequencingPolicy getSequencingPolicyFor(Object listener) {
        AsynchronousEventListener annotation = findAnnotation(listener.getClass(), AsynchronousEventListener.class);
        if (annotation == null) {
            return new SequentialPolicy();
        }

        Class<? extends SequencingPolicy> policyClass = annotation.sequencingPolicyClass();
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

    @Override
    public Object getTarget() {
        return annotatedEventListener;
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

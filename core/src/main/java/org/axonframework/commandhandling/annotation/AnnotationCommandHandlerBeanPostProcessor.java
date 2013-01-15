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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.common.annotation.AbstractAnnotationHandlerBeanPostProcessor;
import org.axonframework.domain.AggregateRoot;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link
 * org.axonframework.commandhandling.annotation.CommandHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class AnnotationCommandHandlerBeanPostProcessor extends AbstractAnnotationHandlerBeanPostProcessor {

    private CommandBus commandBus;

    @Override
    protected Class<?> getAdapterInterface() {
        return CommandHandler.class;
    }

    @Override
    protected boolean isPostProcessingCandidate(Class<?> targetClass) {
        return isNotCommandHandlerSubclass(targetClass) && hasCommandHandlerMethod(targetClass);
    }

    @Override
    protected AnnotationCommandHandlerAdapter initializeAdapterFor(Object bean) {
        ensureCommandBusInitialized();
        return AnnotationCommandHandlerAdapter.subscribe(bean, commandBus);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    private void ensureCommandBusInitialized() {
        // if no CommandBus is set, find one in the application context
        if (commandBus == null) {
            Map<String, CommandBus> beans = getApplicationContext().getBeansOfType(CommandBus.class);
            if (beans.size() != 1) {
                throw new IllegalStateException(
                        "If no specific CommandBus is provided, the application context must "
                                + "contain exactly one bean of type CommandBus. The current application context has: "
                                + beans.size());
            } else {
                this.commandBus = beans.entrySet().iterator().next().getValue();
            }
        }
    }

    private boolean isNotCommandHandlerSubclass(Class<?> beanClass) {
        return !CommandHandler.class.isAssignableFrom(beanClass) && !AggregateRoot.class.isAssignableFrom(beanClass);
    }

    private boolean hasCommandHandlerMethod(Class<?> beanClass) {
        final AtomicBoolean result = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(beanClass, new HasEventHandlerAnnotationMethodCallback(result));
        return result.get();
    }

    /**
     * Sets the event bus to which detected event listeners should be subscribed. If none is provided, the event bus
     * will be automatically detected in the application context.
     *
     * @param commandBus the event bus to subscribe detected event listeners to
     */
    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    private static final class HasEventHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {

        private final AtomicBoolean result;

        private HasEventHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if (method.isAnnotationPresent(org.axonframework.commandhandling.annotation.CommandHandler.class)) {
                result.set(true);
            }
        }
    }
}
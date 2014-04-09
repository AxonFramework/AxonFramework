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

package org.axonframework.commandhandling.annotation;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AbstractMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandlerInspector;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.axonframework.common.ReflectionUtils.fieldsOf;

/**
 * Handler inspector that finds annotated constructors and methods on a given aggregate type and provides handlers for
 * those methods.
 *
 * @param <T> the type of aggregate inspected by this class
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateCommandHandlerInspector<T extends AggregateRoot> {

    private static final Logger logger = LoggerFactory.getLogger(AggregateCommandHandlerInspector.class);

    private final List<ConstructorCommandMessageHandler<T>> constructorCommandHandlers =
            new LinkedList<ConstructorCommandMessageHandler<T>>();
    private final List<AbstractMessageHandler> handlers;

    /**
     * Initialize an MethodMessageHandlerInspector, where the given <code>annotationType</code> is used to annotate the
     * Event Handler methods.
     *
     * @param targetType               The targetType to inspect methods on
     * @param parameterResolverFactory The strategy for resolving parameter values
     */
    @SuppressWarnings({"unchecked"})
    protected AggregateCommandHandlerInspector(Class<T> targetType, ParameterResolverFactory parameterResolverFactory) {
        MethodMessageHandlerInspector inspector = MethodMessageHandlerInspector.getInstance(targetType,
                                                                                            CommandHandler.class,
                                                                                            parameterResolverFactory,
                                                                                            true);
        handlers = new ArrayList<AbstractMessageHandler>(inspector.getHandlers());
        processNestedEntityCommandHandlers(targetType, targetType, parameterResolverFactory);
        for (Constructor constructor : targetType.getConstructors()) {
            if (constructor.isAnnotationPresent(CommandHandler.class)) {
                constructorCommandHandlers.add(
                        ConstructorCommandMessageHandler.forConstructor(constructor, parameterResolverFactory));
            }
        }
    }

    private void processNestedEntityCommandHandlers(Class<?> aggregateRoot, Class<?> targetType,
                                                    ParameterResolverFactory parameterResolverFactory,
                                                    Field... fieldStack) {
        for (Field field : fieldsOf(targetType)) {
            if (field.isAnnotationPresent(CommandHandlingMember.class)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Field {}.{} is annotated with @CommandHandlingMember. "
                                         + "Checking {} for Command Handlers",
                                 targetType.getSimpleName(), field.getName(), field.getType().getSimpleName()
                    );
                }
                MethodMessageHandlerInspector fieldInspector = MethodMessageHandlerInspector
                        .getInstance(field.getType(), CommandHandler.class, parameterResolverFactory, true);
                Field[] stack;
                if (fieldStack == null) {
                    stack = new Field[]{field};
                } else {
                    stack = Arrays.copyOf(fieldStack, fieldStack.length + 1);
                }
                stack[stack.length - 1] = field;
                ReflectionUtils.ensureAccessible(field);
                for (MethodMessageHandler fieldHandler : fieldInspector.getHandlers()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found a Command Handler in {} on path {}.{}",
                                     field.getType().getSimpleName(), aggregateRoot.getSimpleName(), createPath(stack));
                    }
                    handlers.add(new FieldForwardingMethodMessageHandler(stack, fieldHandler));
                }
                processNestedEntityCommandHandlers(aggregateRoot, field.getType(), parameterResolverFactory, stack);
            }
        }
    }

    private String createPath(Field[] fields) {
        StringBuilder sb = new StringBuilder();
        for (Field field : fields) {
            sb.append(field.getName())
              .append(".");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Returns a list of constructor handlers on the given aggregate type.
     *
     * @return a list of constructor handlers on the given aggregate type
     */
    public List<ConstructorCommandMessageHandler<T>> getConstructorHandlers() {
        return constructorCommandHandlers;
    }

    /**
     * Returns the list of handlers found on target type.
     *
     * @return the list of handlers found on target type
     */
    public List<AbstractMessageHandler> getHandlers() {
        return handlers;
    }

    private static class FieldForwardingMethodMessageHandler extends AbstractMessageHandler {

        private final Field[] fields;
        private final AbstractMessageHandler handler;

        public FieldForwardingMethodMessageHandler(Field[] fields, AbstractMessageHandler handler) {
            super(handler);
            this.fields = fields;
            this.handler = handler;
        }

        @Override
        public Object invoke(Object target, Message message) throws InvocationTargetException, IllegalAccessException {
            Object entity = target;
            for (Field field : fields) {
                entity = field.get(entity);
                if (entity == null) {
                    throw new IllegalStateException(
                            "There is no instance available in the '" + field.getName() + "' field, declared in '" +
                                    field.getDeclaringClass().getName() + "'. The command cannot be handled."
                    );
                }
            }
            return handler.invoke(entity, message);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            return handler.getAnnotation(annotationType);
        }
    }
}

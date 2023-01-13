/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.config;

import java.util.function.BiFunction;

/**
 * Type-safe implementation of the {@link ComponentDecorator}. Will check whether the component matches the target type
 * before executing the decorating function. Additionally, if wanted, it will call the lifecycle handlers on the
 * original component.
 *
 * @author Mitchell Herrijgers
 * @since 4.7.0
 */
public class ComponentTypeSafeDecorator<T> implements ComponentDecorator {

    private final Class<T> componentClass;
    private final boolean registerOriginalLifeCycleHandlers;
    private final BiFunction<Configuration, T, T> decorator;

    /**
     * Creates a new {@link ComponentTypeSafeDecorator} that can decorate a component of the {@param componentClass}.
     *
     * @param componentClass            The component class which this decorator should decorate.
     * @param registerOriginalLifeCycleHandlers Whether the lifecycle handlers of the wrapped component should be registered.
     * @param decoratorFunction         The function that returns the decorated component based on the given
     *                                  configuration and the original.
     */
    public ComponentTypeSafeDecorator(Class<T> componentClass,
                                      boolean registerOriginalLifeCycleHandlers,
                                      BiFunction<Configuration, T, T> decoratorFunction) {
        this.componentClass = componentClass;
        this.registerOriginalLifeCycleHandlers = registerOriginalLifeCycleHandlers;
        this.decorator = decoratorFunction;
    }

    /**
     * Decorates the given component using the {@link #decorator} if it matches the {@link #componentClass}, otherwise
     * returns the original.
     *
     * @param configuration The Axon Configuration
     * @param component     The component to be decorated
     * @return The decorated component
     */
    public Object decorate(Configuration configuration, Object component) {
        if (componentClass.isAssignableFrom(component.getClass())) {
            T result = decorator.apply(configuration, (T) component);
            if (registerOriginalLifeCycleHandlers) {
                LifecycleHandlerInspector.registerLifecycleHandlers(configuration, component);
            }
            return result;
        }
        return component;
    }
}

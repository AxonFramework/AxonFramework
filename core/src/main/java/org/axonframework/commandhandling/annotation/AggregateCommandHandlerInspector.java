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

package org.axonframework.commandhandling.annotation;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.util.AbstractHandlerInspector;

import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.List;

/**
 * Handler inspector that finds annotated constructors and methods on a given aggregate type and provides handlers for
 * those methods.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateCommandHandlerInspector<T extends AggregateRoot> extends AbstractHandlerInspector {

    private final List<ConstructorCommandHandler<T>> constructorCommandHandlers = new LinkedList<ConstructorCommandHandler<T>>();

    /**
     * Initialize an AbstractHandlerInspector, where the given <code>annotationType</code> is used to annotate the
     * Event Handler methods.
     *
     * @param targetType The targetType to inspect methods on
     */
    @SuppressWarnings({"unchecked"})
    protected AggregateCommandHandlerInspector(Class<T> targetType) {
        super(targetType, CommandHandler.class);
        for (Constructor constructor : targetType.getConstructors()) {
            if (constructor.isAnnotationPresent(CommandHandler.class)) {
                Class<?>[] parameters = constructor.getParameterTypes();
                constructorCommandHandlers.add(
                        new ConstructorCommandHandler<T>(constructor, parameters[0], parameters.length == 2));
            }
        }
    }

    /**
     * Returns a list of constructor handlers on the given aggregate type.
     *
     * @return a list of constructor handlers on the given aggregate type
     */
    public List<ConstructorCommandHandler<T>> getConstructorHandlers() {
        return constructorCommandHandlers;
    }
}

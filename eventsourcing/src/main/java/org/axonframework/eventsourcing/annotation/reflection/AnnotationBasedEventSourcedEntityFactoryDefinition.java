/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.annotation.reflection;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactoryDefinition;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;

/**
 * Definition for a constructor-based {@link EventSourcedEntityFactory} for an {@link EventSourcedEntity} annotated
 * class. This is the default implementation of the {@link EventSourcedEntityFactoryDefinition} for the
 * {@link EventSourcedEntity} annotation.
 * <p>
 * The {@link AnnotationBasedEventSourcedEntityFactory} that is constructed through this class scans the
 * {@link EventSourcedEntity} annotated class for a constructor that matches the given {@code idType} or zero-argument
 * constructor. If no such constructor is found, an {@link IllegalArgumentException} is thrown at runtime.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotationBasedEventSourcedEntityFactoryDefinition
        implements EventSourcedEntityFactoryDefinition<Object, Object> {

    @Override
    public EventSourcedEntityFactory<Object, Object> createFactory(
            @Nonnull Class<Object> entityType,
            @Nonnull Class<Object> idType,
            @Nonnull Configuration configuration
    ) {
        return new AnnotationBasedEventSourcedEntityFactory<>(
                entityType,
                idType,
                configuration.getComponent(ParameterResolverFactory.class),
                configuration.getComponent(MessageTypeResolver.class)
        );
    }
}

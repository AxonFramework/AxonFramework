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
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactoryDefinition;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;

import java.util.Set;

/**
 * Definition for an annotation-based {@link EventSourcedEntityFactory} that constructs an
 * {@link EventSourcedEntity}-annotated class. This is the default implementation of the
 * {@link EventSourcedEntityFactoryDefinition} for the {@link EventSourcedEntity} annotation.
 * <p>
 * The {@link AnnotationBasedEventSourcedEntityFactory} that is constructed through this class scans
 * {@link EntityCreator}-annotated static methods and constructors. See the {@link EntityCreator} documentation
 * for more information on how to use it.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotationBasedEventSourcedEntityFactoryDefinition
        implements EventSourcedEntityFactoryDefinition<Object, Object> {

    @Override
    public EventSourcedEntityFactory<Object, Object> createFactory(
            @Nonnull Class<Object> entityType,
            @Nonnull Set<Class<? extends Object>> entitySubTypes,
            @Nonnull Class<Object> idType,
            @Nonnull Configuration configuration
    ) {
        return new AnnotationBasedEventSourcedEntityFactory<>(
                entityType,
                idType,
                entitySubTypes,
                configuration.getComponent(ParameterResolverFactory.class),
                configuration.getComponent(MessageTypeResolver.class),
                configuration.getComponent(EventConverter.class)
        );
    }
}

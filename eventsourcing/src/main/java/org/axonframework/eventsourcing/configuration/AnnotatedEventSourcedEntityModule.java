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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ConstructorUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.configuration.BaseModule;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.annotation.CriteriaResolverDefinition;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactoryDefinition;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.annotation.EntityIdResolverDefinition;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.entity.annotation.AnnotatedEntityMetamodel;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.ConstructorUtils.getConstructorFunctionWithZeroArguments;

/**
 * Annotation-based implementation of the {@link EventSourcedEntityModule}. Expects the {@link EventSourcedEntity}
 * annotation on the given {@code entityType}, throwing an {@link IllegalArgumentException} when not present. It will
 * construct a {@link EventSourcedEntityModule#declarative(Class, Class) declarative module} based on the configuration
 * provided by the annotation.
 *
 * @param <I> The type of identifier used to identify the event-sourced entity that's being built.
 * @param <E> The type of the event-sourced entity being built.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
class AnnotatedEventSourcedEntityModule<I, E>
        extends BaseModule<AnnotatedEventSourcedEntityModule<I, E>>
        implements EventSourcedEntityModule<I, E> {

    private final Class<I> idType;
    private final Class<E> entityType;
    private final Set<Class<? extends E>> concreteTypes;

    AnnotatedEventSourcedEntityModule(@Nonnull Class<I> idType, @Nonnull Class<E> entityType) {
        super("AnnotatedEventSourcedEntityModule<%s, %s>".formatted(idType.getName(), entityType.getName()));

        this.idType = requireNonNull(idType, "The idType may not be null.");
        this.entityType = requireNonNull(entityType, "The entityType may not be null.");

        Map<String, Object> annotationAttributes = AnnotationUtils
                .findAnnotationAttributes(entityType, EventSourcedEntity.class)
                .orElseThrow(() -> new IllegalArgumentException("The given class is not an @EventSourcedEntity."));
        this.concreteTypes = getConcreteEntityTypes(annotationAttributes);

        componentRegistry(cr -> cr.registerModule(
                EventSourcedEntityModule
                        .declarative(idType, entityType)
                        .messagingModel((c, b) -> this.buildMetaModel(c))
                        .entityFactory(entityFactory(annotationAttributes, concreteTypes))
                        .criteriaResolver(criteriaResolver(annotationAttributes))
                        .entityIdResolver(entityIdResolver(annotationAttributes)))
        );
    }

    private AnnotatedEntityMetamodel<E> buildMetaModel(@Nonnull Configuration c) {
        if (!concreteTypes.isEmpty()) {
            return AnnotatedEntityMetamodel.forPolymorphicType(
                    entityType,
                    concreteTypes,
                    c.getComponent(ParameterResolverFactory.class),
                    c.getComponent(MessageTypeResolver.class),
                    c.getComponent(MessageConverter.class),
                    c.getComponent(EventConverter.class)
            );
        }

        return AnnotatedEntityMetamodel.forConcreteType(
                entityType,
                c.getComponent(ParameterResolverFactory.class),
                c.getComponent(MessageTypeResolver.class),
                c.getComponent(MessageConverter.class),
                c.getComponent(EventConverter.class)
        );
    }

    @SuppressWarnings("unchecked")
    private ComponentBuilder<CriteriaResolver<I>> criteriaResolver(Map<String, Object> attributes) {
        var criteriaResolverType = (Class<CriteriaResolverDefinition>) attributes.get("criteriaResolverDefinition");
        var criteriaResolverDefinition = ConstructorUtils.getConstructorFunctionWithZeroArguments(criteriaResolverType)
                                                         .get();
        return c -> criteriaResolverDefinition.createEventCriteriaResolver(entityType, idType, c);
    }

    @SuppressWarnings("unchecked")
    private ComponentBuilder<EventSourcedEntityFactory<I, E>> entityFactory(Map<String, Object> attributes,
                                                                            Set<Class<? extends E>> concreteTypes) {
        var type = (Class<EventSourcedEntityFactoryDefinition<E, I>>) attributes.get("entityFactoryDefinition");
        var entityFactoryDefinition = getConstructorFunctionWithZeroArguments(type).get();
        return c -> entityFactoryDefinition.createFactory(entityType, concreteTypes, idType, c);
    }

    @SuppressWarnings("unchecked")
    private ComponentBuilder<EntityIdResolver<I>> entityIdResolver(Map<String, Object> annotationAttributes) {
        var type = (Class<EntityIdResolverDefinition>) annotationAttributes.get("entityIdResolverDefinition");
        var definition = getConstructorFunctionWithZeroArguments(type).get();
        return c -> {
            var component = (AnnotatedEntityMetamodel<E>) c.getComponent(EntityMetamodel.class, entityName());
            return definition.createIdResolver(entityType, idType, component, c);
        };
    }

    private Set<Class<? extends E>> getConcreteEntityTypes(Map<String, Object> attributes) {
        //noinspection unchecked
        Class<? extends E>[] concreteTypes = (Class<? extends E>[]) attributes.get("concreteTypes");
        Arrays.stream(concreteTypes)
              .filter(concreteType -> !entityType.isAssignableFrom(concreteType))
              .forEach(concreteType -> {
                  throw new IllegalArgumentException(
                          ("The declared concrete type [%s] is not assignable to the entity type [%s]. "
                                  + "Please ensure the concrete type is a subclass of the entity type.")
                                  .formatted(concreteType.getName(), entityType.getName())
                  );
              });
        return Set.of(concreteTypes);
    }

    @Override
    public Class<I> idType() {
        return idType;
    }

    @Override
    public Class<E> entityType() {
        return entityType;
    }
}

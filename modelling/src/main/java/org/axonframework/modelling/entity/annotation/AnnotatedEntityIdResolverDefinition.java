/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.annotation.EntityIdResolverDefinition;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * {@link EntityIdResolverDefinition} that converts the payload of incoming messages based on the
 * {@link AnnotatedEntityMetamodel#getExpectedRepresentation(QualifiedName) expected payload type} of the message
 * handler in the model, and then looks for a {@link TargetEntityId}-annotated
 * member in the payload, through the {@link AnnotationBasedEntityIdResolver}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotatedEntityIdResolverDefinition implements EntityIdResolverDefinition {

    @Override
    public <E, ID> EntityIdResolver<ID> createIdResolver(@Nonnull Class<E> entityType,
                                                         @Nonnull Class<ID> idType,
                                                         @Nonnull AnnotatedEntityMetamodel<E> entityMetamodel,
                                                         @Nonnull Configuration configuration) {
        return new AnnotatedEntityIdResolver<>(
                entityMetamodel,
                idType,
                configuration.getComponent(MessageConverter.class),
                new AnnotationBasedEntityIdResolver<>()
        );
    }
}

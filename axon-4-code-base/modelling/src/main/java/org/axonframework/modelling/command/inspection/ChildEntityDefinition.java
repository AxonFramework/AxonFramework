/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import java.lang.reflect.Member;
import java.util.Optional;

/**
 * Interface describing the definition of a Child Entity. These definitions are automatically detected by the {@link
 * AnnotatedAggregateMetaModelFactory} if the definition's implementations is registered in the {@code
 * META-INF/services/org.axonframework.modelling.command.inspection.ChildEntityDefinition} file.
 *
 * @author Allard Buijze
 * @see java.util.ServiceLoader
 * @since 3.0
 */
public interface ChildEntityDefinition {

    /**
     * Inspect the given {@code member}, which is declared on the given {@code declaringEntity} for the presence of a
     * Child Entity.
     *
     * @param member          the member potentially containing a Child entity
     * @param declaringEntity the entity model declaring the field
     * @param <T>             the type of entity on which the field is declared
     * @return an optional that resolved to a ChildEntity if the field represents a child
     */
    <T> Optional<ChildEntity<T>> createChildDefinition(Member member, EntityModel<T> declaringEntity);
}

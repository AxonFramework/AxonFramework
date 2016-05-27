/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model.inspection;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Interface describing the definition of a Child Entity. These definitions are automatically detected by the
 * {@link ModelInspector} if the definition's implementations is registered in the
 * {@code META-INF/services/org.axonframework.commandhandling.model.inspection.ChildEntityDefinition} file.
 *
 * @see java.util.ServiceLoader
 */
public interface ChildEntityDefinition {

    /**
     * Inspect the given {@code field}, which is declared on the given {@code declaringEntity} for the presence of a
     * Child Entity.
     *
     * @param field           The field potentially containing a Child entity
     * @param declaringEntity The entity model declaring the field
     * @param <T>             The type of entity on which the field is declared
     * @return an optional that resolved to a ChildEntity if the field represents a child.
     */
    <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity);

}

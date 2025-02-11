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

package org.axonframework.queryhandling;

import org.axonframework.messaging.QualifiedName;

import java.util.Set;

/**
 * Interface describing a group of {@code QueryHandlers} belonging to a single component.
 * <p>
 * As such, it allows registration of {@code QueryHandlers} through the {@code QueryHandlerRegistry}. Besides handling
 * and registration, it specifies which {@link #supportedQueries() queries} it supports.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface QueryHandlingComponent extends QueryHandler, QueryHandlerRegistry {

    /**
     * All supported {@link QueryMessage queries}, referenced through a {@link QualifiedName}.
     *
     * @return All supported {@link QueryMessage queries}, referenced through a {@link QualifiedName}.
     */
    Set<QualifiedName> supportedQueries();
}

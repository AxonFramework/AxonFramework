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

package org.axonframework.modelling.command;

import javax.annotation.Nonnull;

/**
 * Provides a repository for given aggregate type.
 *
 * @author Milan Savic
 * @since 3.3
 */
@FunctionalInterface
public interface RepositoryProvider {

    /**
     * Provides a repository for given aggregate type.
     *
     * @param aggregateType type of the aggregate
     * @param <T>           type of the aggregate
     * @return repository given for aggregate type
     */
    <T> Repository<T> repositoryFor(@Nonnull Class<T> aggregateType);
}

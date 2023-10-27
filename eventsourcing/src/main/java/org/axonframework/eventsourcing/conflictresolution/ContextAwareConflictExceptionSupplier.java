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

package org.axonframework.eventsourcing.conflictresolution;

/**
 * ConflictExceptionSupplier that is provided with more details of a version conflict.
 *
 * @param <T> The type of exception supplied
 * @author Allard Buijze
 * @since 3.2
 */
@FunctionalInterface
public interface ContextAwareConflictExceptionSupplier<T> {

    /**
     * Creates an instance of an exception indicating a conflict described by the given {@code conflictDescription}.
     *
     * @param conflictDescription Describing details of the conflict detected
     * @return the exception describing the conflict
     */
    T supplyException(ConflictDescription conflictDescription);
}

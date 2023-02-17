/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.modelling.command;

/**
 * Enumeration containing the possible creation policies for aggregates.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public enum AggregateCreationPolicy {
    /**
     * Always create a new instance of the aggregate on invoking the method. Fail if already exists.
     */
    ALWAYS,
    /**
     * Create a new instance of the aggregate when it is not found.
     */
    CREATE_IF_MISSING,
    /**
     * Expect instance of the aggregate to exist. Fail if missing.
     */
    NEVER
}

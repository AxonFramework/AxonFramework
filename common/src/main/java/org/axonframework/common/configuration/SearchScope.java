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

package org.axonframework.common.configuration;

/**
 * Enumeration stating on what levels to search for a {@link Component} within the {@link ComponentRegistry}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public enum SearchScope {

    /**
     * Searches for {@link Component Components} in the current {@link ComponentRegistry} <b>and</b> any ancestors of
     * the current registry.
     */
    ALL,

    /**
     * Searches for {@link Component Components} in the current {@link ComponentRegistry} only, disregarding any
     * ancestors.
     */
    CURRENT,

    /**
     * Searches for {@link Component Components} in the ancestors of the current {@link ComponentRegistry} only.
     */
    ANCESTORS;
}

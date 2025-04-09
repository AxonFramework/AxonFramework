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

package org.axonframework.configuration;

/**
 * Enumeration describing how a {@link ComponentRegistry} should react when a {@link Component} is to be overridden
 * during a {@link ComponentRegistry#registerComponent(ComponentDefinition)} invocation.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @see ComponentRegistry#setOverridePolicy(OverridePolicy)
 * @since 5.0.0
 */
public enum OverridePolicy {
    /**
     * Overriding is allowed at all times.
     */
    ALLOW,
    /**
     * Overriding a components results in a WARN-level log message.
     */
    WARN,
    /**
     * Trying to override results in a {@link ComponentOverrideException}.
     */
    REJECT
}

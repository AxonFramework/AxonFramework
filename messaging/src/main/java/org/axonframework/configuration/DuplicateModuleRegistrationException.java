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
 * Exception indicating that a duplicate registration of modules has been detected. This happens when two modules are
 * registered under the same name.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class DuplicateModuleRegistrationException extends RuntimeException {

    /**
     * Initialize the exception indicating that the given {@code module} failed to register.
     *
     * @param module The module that failed to register.
     */
    public DuplicateModuleRegistrationException(Module module) {
        super("A module with given name already exists: " + module.name());
    }
}

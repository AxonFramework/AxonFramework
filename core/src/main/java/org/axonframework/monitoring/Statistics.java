/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.monitoring;

/**
 * Interface to be implemented by all statistics instances. Statistics can enabled and disabled. When disabled, they may
 * choose only to record activity partially, or none at all, for performance reasons. When enabled, all information
 * provided by the statistics instance should be made available.
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public interface Statistics {

    /**
     * Enable the statistics gathering
     */
    void enable();

    /**
     * Disable the statistics gathering
     */
    void disable();
}

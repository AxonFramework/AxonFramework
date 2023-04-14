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

package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.modelling.command.Repository;

/**
 * Helper class to simplify the creation of bean definitions for components configured in Axon Configuration.
 *
 * @author Allard Buijze
 * @since 4.7.4
 */
public abstract class BeanHelper {

    /**
     * Retrieves the {@link Repository} for given {@code aggregateType} from given {@code configuration}.
     *
     * @param aggregateType The type to find the repository for.
     * @param configuration The configuration from which to retrieve the {@link Repository}.
     * @param <T>           The type of aggregate.
     * @return The {@link Repository} instance for the aggregate.
     * @throws IllegalArgumentException if the given {@code aggregateType} has not been configured.
     */
    public static <T> Repository<T> repository(Class<T> aggregateType, Configuration configuration) {
        return configuration.repository(aggregateType);
    }

    private BeanHelper() {
        // Utility class
    }
}

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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

public class DefaultPersistentStreamMessageSourceFactory implements PersistentStreamMessageSourceFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPersistentStreamMessageSourceFactory.class);
    private final Set<String> usedNames = new HashSet<>();

    @Override
    public PersistentStreamMessageSource build(String name,
                                               PersistentStreamProperties persistentStreamProperties,
                                               ScheduledExecutorService scheduler,
                                               int batchSize,
                                               String context,
                                               Configuration configuration
    ) {
        if (usedNames.contains(name)) {
            logger.warn(
                    "A Persistent Stream connection with Axon Server is uniquely identified based on the name. Another Persistent Stream is started for a given name [{}]. The new connection will overwrite the existing connection.",
                    name);
        }
        PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                name, configuration, persistentStreamProperties, scheduler, batchSize, context
        );
        usedNames.add(name);
        return messageSource;
    }
}

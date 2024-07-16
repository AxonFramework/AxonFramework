/*
 * Copyright (c) 2010-2024. AxonIQ
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
import org.axonframework.config.SubscribableMessageSourceDefinition;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.concurrent.ScheduledExecutorService;

public class PersistentStreamMessageSourceDefinition implements SubscribableMessageSourceDefinition<EventMessage<?>> {

    private final String name;
    private final PersistentStreamProperties persistentStreamProperties;
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final String context;

    public PersistentStreamMessageSourceDefinition(String name, PersistentStreamProperties
            persistentStreamProperties, ScheduledExecutorService scheduler, int batchSize, String context) {

        this.name = name;
        this.persistentStreamProperties = persistentStreamProperties;
        this.scheduler = scheduler;
        this.batchSize = batchSize;
        this.context = context;
    }

    @Override
    public SubscribableMessageSource<EventMessage<?>> create(Configuration configuration) {
        return new PersistentStreamMessageSource(name,
                                                 configuration,
                                                 persistentStreamProperties,
                                                 scheduler,
                                                 batchSize,
                                                 context);
    }
}

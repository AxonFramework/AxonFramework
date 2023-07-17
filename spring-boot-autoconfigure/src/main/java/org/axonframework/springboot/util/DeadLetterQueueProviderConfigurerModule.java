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

package org.axonframework.springboot.util;

import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.springboot.EventProcessorProperties;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * A {@link ConfigurerModule} implementation dedicated towards registering a {@link SequencedDeadLetterQueue} provider
 * with the {@link org.axonframework.config.EventProcessingConfigurer}.
 * <p>
 * Does so through invoking the
 * {@link org.axonframework.config.EventProcessingConfigurer#registerDeadLetterQueueProvider(Function)} operation,
 * utilizing the given {@code deadLetterQueueProvider}. Only processing groups for which the dead-letter queue is
 * {@link EventProcessorProperties.Dlq#isEnabled() enabled} will receive the provided dead-letter queue.
 *
 * @author Gerard Klijs
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class DeadLetterQueueProviderConfigurerModule implements ConfigurerModule {

    private final EventProcessorProperties eventProcessorProperties;
    private final Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider;

    /**
     * Construct a {@link DeadLetterQueueProviderConfigurerModule}, using the given {@code eventProcessorProperties} to
     * decide which processing groups receive the {@link SequencedDeadLetterQueue} from the given
     * {@code deadLetterQueueProvider}.
     *
     * @param eventProcessorProperties The properties dictating for which processing groups the dead-letter queue is
     *                                 {@link EventProcessorProperties.Dlq#isEnabled() enabled}.
     * @param deadLetterQueueProvider  The function providing the {@link SequencedDeadLetterQueue}.
     */
    public DeadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider
    ) {
        this.eventProcessorProperties = eventProcessorProperties;
        this.deadLetterQueueProvider = deadLetterQueueProvider;
    }

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        configurer.eventProcessing().registerDeadLetterQueueProvider(
                processingGroup -> dlqEnabled(eventProcessorProperties.getProcessors(), processingGroup)
                        ? deadLetterQueueProvider.apply(processingGroup)
                        : null
        );
    }

    private boolean dlqEnabled(Map<String, EventProcessorProperties.ProcessorSettings> processorSettings,
                               String processingGroup) {
        return Optional.ofNullable(processorSettings.get(processingGroup))
                       .map(EventProcessorProperties.ProcessorSettings::getDlq)
                       .map(EventProcessorProperties.Dlq::isEnabled)
                       .orElse(false);
    }
}

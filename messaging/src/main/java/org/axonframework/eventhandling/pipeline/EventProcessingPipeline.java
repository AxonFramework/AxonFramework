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

package org.axonframework.eventhandling.pipeline;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;

/**
 * The EventProcessingPipeline interface defines a contract for processing a list of events in the context of a specific
 * processing context. It allows for the processing of events in a pipeline manner.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventProcessingPipeline {

    /**
     * Processes a list of events in the context of a specific segment and processing context.
     *
     * @param events  the list of messages to be processed
     * @param context the processing context in which the items are processed
     * @return a stream of messages resulting from the processing of the items
     */
    MessageStream.Empty<Message<Void>> process(
            List<? extends EventMessage<?>> events,
            ProcessingContext context
    );
}

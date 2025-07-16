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
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collections;
import java.util.List;

// TODO: Decide, alternative for InterceptingEventHandlingComponent
public class InterceptingEventProcessingPipeline implements EventProcessingPipeline {

    private final EventProcessingPipeline next;
    private final MessageHandlerInterceptors messageHandlerInterceptors;

    public InterceptingEventProcessingPipeline(EventProcessingPipeline next,
                                               MessageHandlerInterceptors messageHandlerInterceptors) {
        this.next = next;
        this.messageHandlerInterceptors = messageHandlerInterceptors;
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events, ProcessingContext context,
                                                      Segment segment) {
        // todo: decide if we intercept first everything and then pass to next as a batch or one by one

        // one by one below
        MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
        for (var event : events) {
            DefaultInterceptorChain<EventMessage<?>, ?> chain =
                    new DefaultInterceptorChain<>(
                            null,
                            messageHandlerInterceptors.toList(),
                            (msg, ctx) -> next.process(Collections.singletonList(msg), ctx, segment)
                    );
            MessageStream.Empty<Message<Void>> eventResult = chain.proceed(event, context).ignoreEntries().cast();
            batchResult = batchResult.concatWith(eventResult).ignoreEntries();
        }
        return batchResult;
    }
}

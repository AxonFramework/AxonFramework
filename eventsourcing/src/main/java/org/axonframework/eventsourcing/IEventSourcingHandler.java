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

package org.axonframework.eventsourcing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

// todo: rename to EventSourcingHandler and move the `@EventSourcingHandler` annotation to annotations package
interface IEventSourcingHandler extends EventHandler {

    // todo: should it be ResultMessage / just message or EvolvedStateMessage?
    @Nonnull
    MessageStream.Single<? extends ResultMessage<?>> source(@Nonnull EventMessage<?> event,
                                                            @Nonnull ProcessingContext context);

    @Nonnull
    @Override
    default MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                      @Nonnull ProcessingContext context) {
        return source(event, context).ignoreEntries().cast();
    }
}

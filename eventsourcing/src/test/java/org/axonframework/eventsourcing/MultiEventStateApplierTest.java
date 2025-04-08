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

import org.axonframework.common.Priority;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class MultiEventStateApplierTest {
    @Test
    void appliesEventSourcingHandlersInRegisteredOrder() {
        MultiEventStateApplier<String> applier = new MultiEventStateApplier<>(
                new AdditiveEventStateApplier("one"),
                new AdditiveEventStateApplier("two"),
                new AdditiveEventStateApplier("three")
        );
        String result = applier.apply("base",
                                    new GenericEventMessage<>(new MessageType(String.class), "test-event"),
                                    new StubProcessingContext());
        assertEquals("base:one:two:three", result);
    }

    @Priority(500)
    static class AdditiveEventStateApplier implements EventStateApplier<String> {

        private final String stringToAdd;

        AdditiveEventStateApplier(String stringToAdd) {
            this.stringToAdd = stringToAdd;
        }

        @Override
        public String apply(@NotNull String model, @NotNull EventMessage<?> event,
                            @NotNull ProcessingContext processingContext) {
            return model + ":" + stringToAdd;
        }
    }
}
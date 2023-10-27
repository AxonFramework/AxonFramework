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

package org.axonframework.eventhandling;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.function.Consumer;

import static org.axonframework.utils.EventTestUtils.createEvents;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
class DirectEventProcessingStrategyTest {

    @SuppressWarnings("unchecked")
    @Test
    void eventsPassedToProcessor() {
        List<? extends EventMessage<?>> events = createEvents(10);
        Consumer<List<? extends EventMessage<?>>> mockProcessor = mock(Consumer.class);
        DirectEventProcessingStrategy.INSTANCE.handle(events, mockProcessor);
        verify(mockProcessor).accept(events);
    }
}

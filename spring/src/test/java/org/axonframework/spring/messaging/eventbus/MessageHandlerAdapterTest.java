/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.spring.messaging.eventbus;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.spring.messaging.StubDomainEvent;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;
import org.springframework.messaging.support.GenericMessage;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class MessageHandlerAdapterTest {

    @SuppressWarnings({"unchecked"})
    @Test
    public void testMessageForwarded() {
        EventProcessor mockEventProcessor = mock(EventProcessor.class);
        MessageHandlerAdapter adapter = new MessageHandlerAdapter(mockEventProcessor);

        final StubDomainEvent payload = new StubDomainEvent();
        adapter.handleMessage(new GenericMessage<>(payload));
        adapter.handleMessage(new GenericMessage<>(new StubDomainEvent()));

        verify(mockEventProcessor, times(1)).accept(argThat(new BaseMatcher<EventMessage>() {
            @Override
            public boolean matches(Object o) {
                return ((o instanceof EventMessage) && ((EventMessage) o).getPayload().equals(payload));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Event with correct payload");
            }
        }));
    }
}

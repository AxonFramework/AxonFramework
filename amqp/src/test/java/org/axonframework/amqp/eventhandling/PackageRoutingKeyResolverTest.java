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

package org.axonframework.amqp.eventhandling;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class PackageRoutingKeyResolverTest {

    private PackageRoutingKeyResolver testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new PackageRoutingKeyResolver();
    }

    @Test
    public void testPackageIsReturned() {
        String actual = testSubject.resolveRoutingKey(new GenericEventMessage<>(new Object()));
        assertEquals("java.lang", actual);
    }

    @Test
    public void testOnlyPayloadTypeIsUsed() {
        EventMessage mockMessage = mock(EventMessage.class);
        when(mockMessage.getPayloadType()).thenReturn(RoutingKeyResolver.class);
        String actual = testSubject.resolveRoutingKey(mockMessage);
        assertEquals("org.axonframework.amqp.eventhandling", actual);
        // make sure only the payload type method is invoked
        verify(mockMessage).getPayloadType();
        verifyNoMoreInteractions(mockMessage);
    }
}

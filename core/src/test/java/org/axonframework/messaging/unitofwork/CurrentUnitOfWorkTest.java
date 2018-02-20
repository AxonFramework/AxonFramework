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

package org.axonframework.messaging.unitofwork;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

/**
 * @author Allard Buijze
 */
public class CurrentUnitOfWorkTest {

    @Before
    public void setUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        initializeMDC();
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        clearMDC();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetSession_NoCurrentSession() {
        CurrentUnitOfWork.get();
    }

    @Test
    public void testSetSession() {
        UnitOfWork<?> mockUnitOfWork = mock(UnitOfWork.class);
        CurrentUnitOfWork.set(mockUnitOfWork);
        assertSame(mockUnitOfWork, CurrentUnitOfWork.get());

        CurrentUnitOfWork.clear(mockUnitOfWork);
        assertFalse(CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testNotCurrentUnitOfWorkCommitted() {
        DefaultUnitOfWork<?> outerUoW = new DefaultUnitOfWork<>(null);
        outerUoW.start();
        new DefaultUnitOfWork<>(null).start();
        try {
            outerUoW.commit();
        } catch (IllegalStateException e) {
            return;
        }
        throw new AssertionError("The unit of work is not the current");
    }

    @Test
    public void testAddMetaDataLoggingContext_SingleUnitOfWork_Ok() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> entries = new HashMap<>();
        entries.put("eventId", eventId);
        entries.put("foo", "baz");
        MetaData metaData = MetaData.from(entries);
        UnitOfWork<Message<?>> uow = new DefaultUnitOfWork<>(mockMessage(metaData));
        uow.start();

        assertEquals(MDC.get("eventId"), metaData.get("eventId").toString());
        assertEquals(MDC.get("foo"), metaData.get("foo"));
        assertNull(MDC.get("test"));
    }

    @Test
    public void testAddMetaDataLoggingContext_MultipleUnitsOfWork_Ok() {
        initializeMDC();

        Map<String, Object> entries1 = new HashMap<>();
        entries1.put("uowId", 1);
        MetaData metaData1 = MetaData.from(entries1);
        UnitOfWork<Message<?>> uow1 = new DefaultUnitOfWork<>(mockMessage(metaData1));
        uow1.start();

        assertEquals("1", MDC.get("uowId"));
        assertEquals("bar", MDC.get("foo"));

        Map<String, Object> entries2 = new HashMap<>();
        entries2.put("uowId", 2);
        entries2.put("correlationId", "ABC");
        MetaData metaData2 = MetaData.from(entries2);
        UnitOfWork<Message<?>> uow2 = new DefaultUnitOfWork<>(mockMessage(metaData2));
        uow2.start();

        assertEquals("2", MDC.get("uowId"));
        assertEquals("ABC", MDC.get("correlationId"));
        assertEquals("bar", MDC.get("foo"));

        uow2.commit();

        assertNull(MDC.get("correlationId"));
        assertEquals("1", MDC.get("uowId"));
        assertEquals("bar", MDC.get("foo"));

        uow1.rollback();

        assertNull(MDC.get("uowId"));
        assertNull(MDC.get("correlationId"));
        assertEquals("bar", MDC.get("foo"));
    }

    @Test
    public void testAddMetaDataLoggingContext_NullMessage_Ok() {
        UnitOfWork<Message<?>> uow = new DefaultUnitOfWork<>(null);
        uow.start();

        assertEquals(1, MDC.getMDCAdapter().getCopyOfContextMap().size());
        assertEquals("bar", MDC.get("foo"));
    }

    @Test
    public void testAddMetaDataLoggingContext_NullMetaData_Ok() {
        UnitOfWork<Message<?>> uow = new DefaultUnitOfWork<>(mockMessage(null));
        uow.start();

        assertEquals(1, MDC.getMDCAdapter().getCopyOfContextMap().size());
        assertEquals("bar", MDC.get("foo"));
    }

    private Message<?> mockMessage(MetaData metaData) {
        return new GenericMessage<>(new Object(), metaData);
    }

    private void initializeMDC() {
        Map<String, String> initValues = new HashMap<>();
        initValues.put("foo", "bar");
        MDC.setContextMap(initValues);
    }

    private void clearMDC() {
        MDC.clear();
    }
}

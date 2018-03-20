/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link QueryUpdateEmitterList}.
 *
 * @author Milan Savic
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryUpdateEmitterListTest {

    @Mock
    private QueryUpdateEmitter<String> emitter1;
    @Mock
    private QueryUpdateEmitter<String> emitter2;

    @Test
    public void testAddingAndEmitting() {
        QueryUpdateEmitterList list = new QueryUpdateEmitterList();
        when(emitter1.emit("Update")).thenReturn(true);

        list.add("query", String.class, emitter1);
        boolean emitResult = list.emit("query", ResponseTypes.instanceOf(String.class), "Update");

        verify(emitter1).emit("Update");
        assertTrue(emitResult);
    }

    @Test
    public void testAddingAndEmittingWhenOneEmitFails() {
        QueryUpdateEmitterList list = new QueryUpdateEmitterList();
        when(emitter1.emit("Update")).thenReturn(false);
        when(emitter2.emit("Update")).thenReturn(true);
        when(emitter2.emit("Update1")).thenReturn(true);

        list.add("query", String.class, emitter1);
        list.add("query", String.class, emitter2);

        boolean emitResult = list.emit("query", ResponseTypes.instanceOf(String.class), "Update");

        verify(emitter1).emit("Update");
        verify(emitter2).emit("Update");
        assertFalse(emitResult);

        emitResult = list.emit("query", ResponseTypes.instanceOf(String.class), "Update1");
        verify(emitter1, times(0)).emit("Update1");
        verify(emitter2).emit("Update1");
        assertTrue(emitResult);
    }

    @Test
    public void testAddingAndEmittingWhenOneEmitThrowsAnException() {
        QueryUpdateEmitterList list = new QueryUpdateEmitterList();
        RuntimeException toBeThrown = new RuntimeException("oops");
        when(emitter1.emit("Update")).thenThrow(toBeThrown);
        when(emitter2.emit("Update")).thenReturn(true);
        when(emitter2.emit("Update1")).thenReturn(true);

        list.add("query", String.class, emitter1);
        list.add("query", String.class, emitter2);

        boolean emitResult = list.emit("query", ResponseTypes.instanceOf(String.class), "Update");

        verify(emitter1).emit("Update");
        verify(emitter1).error(toBeThrown);
        verify(emitter2).emit("Update");
        assertFalse(emitResult);

        emitResult = list.emit("query", ResponseTypes.instanceOf(String.class), "Update1");
        verify(emitter1, times(0)).emit("Update1");
        verify(emitter2).emit("Update1");
        assertTrue(emitResult);
    }

    @Test
    public void testEmittingToEmptyList() {
        QueryUpdateEmitterList list = new QueryUpdateEmitterList();

        boolean emitResult = list.emit("query", ResponseTypes.instanceOf(String.class), "Update");

        assertFalse(emitResult);
    }

    @Test
    public void testAddingAndCompleting() {
        QueryUpdateEmitterList list = new QueryUpdateEmitterList();

        list.add("query", String.class, emitter1);
        list.add("query", String.class, emitter2);

        list.complete("query", ResponseTypes.instanceOf(String.class));

        assertTrue(list.filter("query", ResponseTypes.instanceOf(String.class)).isEmpty());
    }
}

/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.saga.annotation;

import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SagaMethodMessageHandlerTest {

    @Test
    public void testHandlersNotConsideredEqualOnSameParameters() throws Exception {
        SagaMethodMessageHandler handler1 = SagaMethodMessageHandler.getInstance(MethodMessageHandler.createFor(getClass().getDeclaredMethod("handlerMethodOne", String.class), null, ClasspathParameterResolverFactory.forClass(getClass())));
        SagaMethodMessageHandler handler2 = SagaMethodMessageHandler.getInstance(MethodMessageHandler.createFor(getClass().getDeclaredMethod("handlerMethodTwo", String.class), null, ClasspathParameterResolverFactory.forClass(getClass())));

        assertNotEquals(0, handler1.compareTo(handler2));
        assertNotEquals(handler1, handler2);
    }

    @Test
    public void testHandlersForSameMethodConsideredEqual() throws Exception {
        SagaMethodMessageHandler handler1 = SagaMethodMessageHandler.getInstance(MethodMessageHandler.createFor(getClass().getDeclaredMethod("handlerMethodOne", String.class), null, ClasspathParameterResolverFactory.forClass(getClass())));
        SagaMethodMessageHandler handler2 = SagaMethodMessageHandler.getInstance(MethodMessageHandler.createFor(getClass().getDeclaredMethod("handlerMethodOne", String.class), null, ClasspathParameterResolverFactory.forClass(getClass())));
        assertEquals(0, handler1.compareTo(handler2));
        assertEquals(handler1, handler2);
    }

    @Test
    public void testHandlersConsideredUnequalToNoHandler() throws Exception {
        SagaMethodMessageHandler handler1 = SagaMethodMessageHandler.getInstance(MethodMessageHandler.createFor(getClass().getDeclaredMethod("handlerMethodOne", String.class), null, ClasspathParameterResolverFactory.forClass(getClass())));
        SagaMethodMessageHandler handler2 = SagaMethodMessageHandler.noHandler();
        assertNotEquals(0, handler1.compareTo(handler2));
        assertNotEquals(handler1, handler2);
    }

    @SagaEventHandler(associationProperty = "toString")
    public void handlerMethodOne(String param) {
    }

    @SagaEventHandler(associationProperty = "hashCode")
    public void handlerMethodTwo(String param) {
    }

    @SagaEventHandler(associationProperty = "toString", keyName = "test")
    public void handlerMethodThree(String param) {
    }

}
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

package org.axonframework.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.Test;

import com.springsource.insight.collection.OperationCollectionAspectSupport;
import com.springsource.insight.collection.OperationCollectionAspectTestSupport;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;

public class EventHandlerOperationCollectionAspectTests extends OperationCollectionAspectTestSupport {
    
    @Test
    public void annotatedEventHandlerOperationCollected() {
        new TestEventHandler().handleEvent(new TestEvent());
        
        Operation op = getLastEntered(Operation.class);

        assertEquals("org.axonframework.insight.EventHandlerOperationCollectionAspectTests$TestEvent", op.get("eventType"));
        assertEquals("handleEvent", op.getSourceCodeLocation().getMethodName());
    }
    
    @Test
    public void eventListenerOperationCollected() {
        new TestEventHandler().handle(
        		new GenericEventMessage<TestEvent>(
        				new TestEvent(),
                        Collections.singletonMap("someKey", (Object) "someValue")));
        
        Operation op = getLastEntered(Operation.class);

        assertEquals("org.axonframework.insight.EventHandlerOperationCollectionAspectTests$TestEvent", op.get("eventType"));
        assertEquals("handle", op.getSourceCodeLocation().getMethodName());
        OperationMap map = op.get("metaData", OperationMap.class);
        assertNotNull("EventMessage metadata missing in operation", map);
        assertEquals(1, map.size());
        assertEquals("someValue", map.get("someKey"));
    }
    
    @Override
    public OperationCollectionAspectSupport getAspect() {
        return EventHandlerOperationCollectionAspect.aspectOf();
    }
    
    static class TestEvent {}
    
    static class TestEventHandler implements EventListener {
        @EventHandler
        void handleEvent(TestEvent event) {}

        public void handle(EventMessage event) {}
    }

}
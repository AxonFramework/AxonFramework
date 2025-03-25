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

package org.axonframework.test.fixture;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.hamcrest.Matcher;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;

public interface AxonTestPhase {

    interface Preparing {

        Executing givenNoPriorActivity();

        default AxonTestPhase.Executing givenEvent(Object payload) {
            return givenEvent(payload, MetaData.emptyInstance());
        }

        default AxonTestPhase.Executing givenEvent(Object payload, Map<String, ?> metaData) {
            return givenEvent(payload, MetaData.from(metaData));
        }

        AxonTestPhase.Executing givenEvent(Object payload, MetaData metaData);
    }

    interface Executing {

        Validation when(Object payload, Map<String, ?> metaData);

        default Validation when(Object payload) {
            return when(payload, new HashMap<>());
        }
    }

    interface Validation {

        Validation expectEvents(EventMessage<?>... expectedEvents);

        Validation expectEvents(Object... expectedEvents);

        default Validation expectNoEvents() {
            return expectEvents();
        }

        default Validation expectException(Class<? extends Throwable> expectedException) {
            return expectException(instanceOf(expectedException));
        }

        Validation expectException(Matcher<?> matcher);
    }
}

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

package org.axonframework.test.af5;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.aggregate.ResultValidator;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;

public interface CommandModelTest {

    interface Executor {

        ResultValidator when(Object payload, Map<String, ?> metaData);

        default ResultValidator when(Object payload) {
            return when(payload, new HashMap<>());
        }
    }

    interface ResultValidator {

        ResultValidator expectEvents(EventMessage<?>... expectedEvents);

        ResultValidator expectEvents(Object... expectedEvents);

        default ResultValidator expectNoEvents() {
            return expectEvents();
        }

        default ResultValidator expectException(Class<? extends Throwable> expectedException) {
            return expectException(instanceOf(expectedException));
        }

        public ResultValidator expectException(Matcher<?> matcher);
    }
}

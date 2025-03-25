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

    interface Given {

        Given noPriorActivity();

        default Given event(Object payload) {
            return event(payload, MetaData.emptyInstance());
        }

        default Given event(Object payload, Map<String, ?> metaData) {
            return event(payload, MetaData.from(metaData));
        }

        Given event(Object payload, MetaData metaData);

        When when();
    }

    interface When {

        When command(Object payload, Map<String, ?> metaData);

        default When command(Object payload) {
            return command(payload, new HashMap<>());
        }

        Then then();
    }

    interface Then {

        Then events(EventMessage<?>... expectedEvents);

        Then events(Object... expectedEvents);

        default Then noEvents() {
            return events();
        }

        default Then exception(Class<? extends Throwable> expectedException) {
            return exception(instanceOf(expectedException));
        }

        Then exception(Matcher<?> matcher);
    }
}

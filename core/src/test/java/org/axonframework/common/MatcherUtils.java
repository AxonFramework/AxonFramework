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

package org.axonframework.common;

import org.axonframework.eventhandling.EventMessage;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import static org.mockito.Matchers.argThat;

/**
 *
 */
public class MatcherUtils {

    public static EventMessage isEventWith(final Class<?> payloadType) {
        return argThat(new BaseMatcher<EventMessage>() {
            @Override
            public boolean matches(Object o) {
                if (!(o instanceof EventMessage)) {
                    return false;
                }
                EventMessage that = (EventMessage) o;
                return payloadType.isInstance(that.getPayload());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Event with payload of type [");
                description.appendValue(payloadType.getName());
                description.appendText("]");
            }
        });
    }
}

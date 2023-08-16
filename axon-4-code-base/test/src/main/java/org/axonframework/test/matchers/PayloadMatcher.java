/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.test.matchers;

import org.axonframework.messaging.Message;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

/**
 * Matcher that matches any message (e.g. Event, Command) who's payload matches the given matcher.
 *
 * @param <T> The type of Message the matcher can match against
 * @author Allard Buijze
 * @since 2.0
 */
public class PayloadMatcher<T extends Message> extends BaseMatcher<T> {

    private final Matcher<?> payloadMatcher;

    /**
     * Constructs an instance with the given {@code payloadMatcher}.
     *
     * @param payloadMatcher The matcher that must match the Message's payload.
     */
    public PayloadMatcher(Matcher<?> payloadMatcher) {
        this.payloadMatcher = payloadMatcher;
    }

    @Override
    public boolean matches(Object item) {
        return Message.class.isInstance(item)
                && payloadMatcher.matches(((Message) item).getPayload());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Message with payload <");
        payloadMatcher.describeTo(description);
        description.appendText(">");
    }
}

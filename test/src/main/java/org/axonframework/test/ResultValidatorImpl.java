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

package org.axonframework.test;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.test.matchers.EqualFieldsMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;

/**
 * Implementation of the ResultValidator. It also acts as a CommandCallback, and registers the actual result.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class ResultValidatorImpl implements ResultValidator, CommandCallback<Object> {

    private final Collection<DomainEventMessage> storedEvents;
    private final Collection<EventMessage> publishedEvents;

    private Object actualReturnValue;
    private Throwable actualException;

    private final Reporter reporter = new Reporter();

    /**
     * Initialize the ResultValidatorImpl with the given <code>storedEvents</code> and <code>publishedEvents</code>.
     *
     * @param storedEvents    The events that were stored during command execution
     * @param publishedEvents The events that were published during command execution
     */
    public ResultValidatorImpl(Collection<DomainEventMessage> storedEvents, Collection<EventMessage> publishedEvents) {
        this.storedEvents = storedEvents;
        this.publishedEvents = publishedEvents;
    }

    @Override
    public ResultValidator expectEvents(Object... expectedEvents) {
        if (publishedEvents.size() != storedEvents.size()) {
            reporter.reportDifferenceInStoredVsPublished(storedEvents, publishedEvents, actualException);
        }
        return expectPublishedEvents(expectedEvents);
    }

    @Override
    public ResultValidator expectEventsMatching(Matcher<List<?>> matcher) {
        if (publishedEvents.size() != storedEvents.size()) {
            reporter.reportDifferenceInStoredVsPublished(storedEvents, publishedEvents, actualException);
        }

        return expectPublishedEventsMatching(matcher);
    }

    @Override
    public ResultValidator expectPublishedEvents(Object... expectedEvents) {
        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
        }

        Iterator<EventMessage> iterator = publishedEvents.iterator();
        for (Object expectedEvent : expectedEvents) {
            EventMessage actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent.getPayload())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator expectPublishedEventsMatching(Matcher<List<?>> matcher) {
        if (!matcher.matches(publishedEvents)) {
            reporter.reportWrongEvent(publishedEvents, descriptionOf(matcher), actualException);
        }
        return this;
    }

    private StringDescription descriptionOf(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        return description;
    }

    @Override
    public ResultValidator expectStoredEvents(Object... expectedEvents) {
        if (expectedEvents.length != storedEvents.size()) {
            reporter.reportWrongEvent(storedEvents, Arrays.asList(expectedEvents), actualException);
        }
        Iterator<DomainEventMessage> iterator = storedEvents.iterator();
        for (Object expectedEvent : expectedEvents) {
            DomainEventMessage actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent)) {
                reporter.reportWrongEvent(storedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator expectStoredEventsMatching(Matcher<List<?>> matcher) {
        if (!matcher.matches(storedEvents)) {
            reporter.reportWrongEvent(storedEvents, descriptionOf(matcher), actualException);
        }
        return this;
    }

    @Override
    public ResultValidator expectVoidReturnType() {
        return expectReturnValue(nullValue());
    }

    @Override
    public ResultValidator expectReturnValue(Object expectedReturnValue) {
        if (expectedReturnValue == null) {
            return expectReturnValue(nullValue());
        }
        return expectReturnValue(equalTo(expectedReturnValue));
    }

    @Override
    public ResultValidator expectReturnValue(Matcher<?> matcher) {
        if (matcher == null) {
            return expectReturnValue(nullValue());
        }
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, description);
        } else if (!matcher.matches(actualReturnValue)) {
            reporter.reportWrongResult(actualReturnValue, description);
        }
        return this;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public ResultValidator expectException(Class<? extends Throwable> expectedException) {
        return expectException(instanceOf(expectedException));
    }

    @Override
    public ResultValidator expectException(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualReturnValue, description);
        }
        if (!matcher.matches(actualException)) {
            reporter.reportWrongException(actualException, description);
        }
        return this;
    }

    @Override
    public void onSuccess(Object result) {
        actualReturnValue = result;
    }

    @Override
    public void onFailure(Throwable cause) {
        actualException = cause;
    }

    /**
     * Makes sure the execution phase has finishes without any Errors. If an error was recorded, it will be thrown
     * immediately. This allow one to distinguish between failed tests, and tests in error.
     */
    public void assertValidRecording() {
        if (actualException instanceof Error) {
            throw (Error) actualException;
        }
    }

    private boolean verifyEventEquality(Object expectedEvent, Object actualEvent) {
        if (!expectedEvent.getClass().equals(actualEvent.getClass())) {
            return false;
        }
        EqualFieldsMatcher<Object> matcher = new EqualFieldsMatcher<Object>(expectedEvent);
        if (!matcher.matches(actualEvent)) {
            reporter.reportDifferentEventContents(expectedEvent.getClass(),
                                                  matcher.getFailedField(),
                                                  matcher.getFailedFieldActualValue(),
                                                  matcher.getFailedFieldExpectedValue());
        }
        return true;
    }
}

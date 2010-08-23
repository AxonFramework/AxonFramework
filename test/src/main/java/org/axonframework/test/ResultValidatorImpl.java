/*
 * Copyright (c) 2010. Axon Framework
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
import org.axonframework.commandhandling.CommandContext;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.axonframework.domain.EventBase;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of the ResultValidator. It also acts as a CommandCallback, and registers the actual result.
 *
 * @author Allard Buijze
 * @since 0.7
 */
class ResultValidatorImpl implements ResultValidator, CommandCallback<Object, Object> {

    private final List<DomainEvent> storedEvents;
    private final List<Event> publishedEvents;

    private Object actualReturnValue;
    private Throwable actualException;

    private final Reporter reporter = new Reporter();

    /**
     * Initialize the ResultValidatorImpl with the given <code>storedEvents</code> and <code>publishedEvents</code>.
     *
     * @param storedEvents    The events that were stored during command execution
     * @param publishedEvents The events that were published during command execution
     */
    public ResultValidatorImpl(List<DomainEvent> storedEvents, List<Event> publishedEvents) {
        this.storedEvents = storedEvents;
        this.publishedEvents = publishedEvents;
    }

    @Override
    public ResultValidator expectEvents(DomainEvent... expectedEvents) {
        if (publishedEvents.size() != storedEvents.size()) {
            reporter.reportDifferenceInStoredVsPublished(storedEvents, publishedEvents);
        }

        return expectPublishedEvents(expectedEvents);
    }

    @Override
    public ResultValidator expectPublishedEvents(Event... expectedEvents) {
        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
        }

        Iterator<Event> iterator = publishedEvents.iterator();
        for (Event expectedEvent : expectedEvents) {
            Event actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent)) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator expectStoredEvents(DomainEvent... expectedEvents) {
        if (expectedEvents.length != storedEvents.size()) {
            reporter.reportWrongEvent(storedEvents, Arrays.asList(expectedEvents), actualException);
        }
        Iterator<DomainEvent> iterator = storedEvents.iterator();
        for (DomainEvent expectedEvent : expectedEvents) {
            DomainEvent actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent)) {
                reporter.reportWrongEvent(storedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator expectVoidReturnType() {
        return expectReturnValue(Void.TYPE);
    }

    @Override
    public ResultValidator expectReturnValue(Object expectedReturnValue) {
        if (expectedReturnValue == null) {
            return expectReturnValue(CoreMatchers.<Object>nullValue());
        }
        return expectReturnValue(CoreMatchers.equalTo(expectedReturnValue));
    }

    @Override
    public ResultValidator expectReturnValue(Matcher<?> matcher) {
        if (matcher == null) {
            return expectReturnValue(CoreMatchers.<Object>nullValue());
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
        return expectException(CoreMatchers.instanceOf(expectedException));
    }

    @Override
    public ResultValidator expectException(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualReturnValue != null) {
            reporter.reportUnexpectedReturnValue(actualReturnValue, description);
        }
        if (!matcher.matches(actualException)) {
            reporter.reportWrongException(actualException, description);
        }
        return this;
    }

    @Override
    public void onSuccess(Object result, CommandContext context) {
        actualReturnValue = result;
    }

    @Override
    public void onFailure(Throwable cause, CommandContext context) {
        actualException = cause;
    }

    private boolean verifyEventEquality(Event expectedEvent, Event actualEvent) {
        if (!expectedEvent.getClass().equals(actualEvent.getClass())) {
            return false;
        }
        verifyEqualFields(expectedEvent.getClass(), expectedEvent, actualEvent);
        return true;
    }

    private void verifyEqualFields(Class<?> aClass, Event expectedEvent, Event actualEvent) {
        for (Field field : aClass.getDeclaredFields()) {
            field.setAccessible(true);
            try {

                Object expected = field.get(expectedEvent);
                Object actual = field.get(actualEvent);
                if ((expected != null && !expected.equals(actual)) || expected == null && actual != null) {
                    reporter.reportDifferentEventContents(expectedEvent.getClass(), field, actual, expected);
                }
            } catch (IllegalAccessException e) {
                throw new FixtureExecutionException("Could not confirm event equality due to an exception", e);
            }
        }
        if (aClass.getSuperclass() != DomainEvent.class
                && aClass.getSuperclass() != EventBase.class
                && aClass.getSuperclass() != Object.class) {
            verifyEqualFields(aClass.getSuperclass(), expectedEvent, actualEvent);
        }
    }
}

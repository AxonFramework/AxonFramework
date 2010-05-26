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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Allard Buijze
 * @since 0.6
 */
class Reporter {

    private static final String NEWLINE = System.getProperty("line.separator");

    /**
     * Report a failed assertion due to a difference in the stored versus the published events.
     *
     * @param storedEvents    The events that were stored
     * @param publishedEvents The events that were published
     */
    public void reportDifferenceInStoredVsPublished(List<DomainEvent> storedEvents, List<Event> publishedEvents) {
        StringBuilder sb = new StringBuilder(
                "The stored events do not match the published events.");
        appendEventOverview(sb, storedEvents, publishedEvents, "Stored events", "Published events");
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error in the ordering or count of events. This is typically a difference that can be shown to the user
     * by enumerating the expected and actual events
     *
     * @param actualEvents   The events that were found
     * @param expectedEvents The events that were expected
     */
    public void reportWrongEvent(List<? extends Event> actualEvents, List<? extends Event> expectedEvents) {
        StringBuilder sb = new StringBuilder(
                "The published events do not match the expected events");
        appendEventOverview(sb, expectedEvents, actualEvents, "Expected", "Actual");
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Reports an error due to an unexpected exception. This means a return value was expected, but an exception was
     * thrown by the command handler
     *
     * @param actualException     The actual exception
     * @param expectedReturnValue The expected return value
     */
    public void reportUnexpectedException(Throwable actualException, Object expectedReturnValue) {
        StringBuilder sb = new StringBuilder("The command handler threw an unexpected exception");
        sb.append(NEWLINE);
        sb.append(NEWLINE);
        sb.append("Expected <");
        if (expectedReturnValue == null) {
            sb.append("null return value");
        } else if (expectedReturnValue == Void.TYPE) {
            sb.append("void return value");
        } else {
            sb.append("return value of type [");
            sb.append(expectedReturnValue.getClass().getSimpleName());
            sb.append("]");
        }
        sb.append("> but got <exception of type [")
                .append(actualException.getClass().getSimpleName());
        sb.append("]>");
        sb.append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Reports an error due to a wrong return value.
     *
     * @param actualReturnValue   The actual return value
     * @param expectedReturnValue The expected return value
     */
    public void reportWrongResult(Object actualReturnValue, Object expectedReturnValue) {
        StringBuilder sb = new StringBuilder("The command handler returned an unexpected value");
        sb.append(NEWLINE)
                .append(NEWLINE)
                .append("Expected <");
        describe(expectedReturnValue, sb);
        sb.append("> but got <");
        describe(actualReturnValue, sb);
        sb.append(">")
                .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to an unexpected return value, while an exception was expected.
     *
     * @param actualReturnValue The actual return value
     * @param expectedException The expected exception
     */
    public void reportUnexpectedReturnValue(Object actualReturnValue, Class<? extends Throwable> expectedException) {
        StringBuilder sb = new StringBuilder("The command handler returned normally, but an exception was expected");
        sb.append(NEWLINE)
                .append(NEWLINE)
                .append("Expected <");
        sb.append("exception of type [")
                .append(expectedException.getSimpleName());
        sb.append("]> but returned with <");
        describe(actualReturnValue, sb);
        sb.append(">")
                .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to a an exception of an unexpected type
     *
     * @param actualException   The actual exception
     * @param expectedException The expected exception type
     */
    public void reportWrongException(Throwable actualException, Class<? extends Throwable> expectedException) {
        StringBuilder sb = new StringBuilder("The command handler threw an exception, but not of the expected type");
        sb.append(NEWLINE)
                .append(NEWLINE)
                .append("Expected <")
                .append("exception of type [")
                .append(expectedException.getSimpleName())
                .append("]> but got <exception of type [")
                .append(actualException.getClass().getSimpleName())
                .append("]>")
                .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to a difference in on of the fields of an event
     *
     * @param eventType The (runtime) type of event the difference was found in
     * @param field     The field that contains the difference
     * @param actual    The actual value of the field
     * @param expected  The expected value of the field
     */
    public void reportDifferentEventContents(Class<? extends Event> eventType, Field field, Object actual,
                                             Object expected) {
        StringBuilder sb = new StringBuilder("One of the events contained different values than expected");
        sb.append(NEWLINE)
                .append(NEWLINE)
                .append("In an event of type [")
                .append(eventType.getSimpleName())
                .append("], the property [")
                .append(field.getName())
                .append("] ");
        if (!eventType.equals(field.getDeclaringClass())) {
            sb.append("(declared in [")
                    .append(field.getDeclaringClass().getSimpleName())
                    .append("]) ");
        }

        sb.append("was not as expected.")
                .append(NEWLINE)
                .append("Expected <")
                .append(actual.toString())
                .append("> but got <")
                .append(expected.toString())
                .append(">")
                .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    private void describe(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null return value");
        } else if (value == Void.TYPE) {
            sb.append("void return type");
        } else {
            sb.append(value.toString());
        }
    }

    private void appendEventOverview(StringBuilder sb, List<? extends Event> leftColumnEvents,
                                     List<? extends Event> rightColumnEvents,
                                     String leftColumnName,
                                     String rightColumnName) {
        List<String> actualTypes = new ArrayList<String>(rightColumnEvents.size());
        List<String> expectedTypes = new ArrayList<String>(leftColumnEvents.size());
        int largestExpectedSize = leftColumnName.length();
        for (Event event : rightColumnEvents) {
            actualTypes.add(event.getClass().getName());
        }
        for (Event event : leftColumnEvents) {
            String simpleName = event.getClass().getName();
            if (simpleName.length() > largestExpectedSize) {
                largestExpectedSize = simpleName.length();
            }
            expectedTypes.add(simpleName);
        }
        sb.append(NEWLINE);
        sb.append(NEWLINE);
        sb.append(leftColumnName);
        pad(sb, leftColumnName.length(), largestExpectedSize, " ");
        sb.append("  |  ")
                .append(rightColumnName)
                .append(NEWLINE);
        pad(sb, 0, largestExpectedSize, "-");
        sb.append("--|--");
        pad(sb, 0, largestExpectedSize, "-");
        sb.append(NEWLINE);
        Iterator<String> actualIterator = actualTypes.iterator();
        Iterator<String> expectedIterator = expectedTypes.iterator();
        while (actualIterator.hasNext() || expectedIterator.hasNext()) {
            boolean difference;
            String expected = "";
            if (expectedIterator.hasNext()) {
                expected = expectedIterator.next();
                sb.append(expected);
                pad(sb, expected.length(), largestExpectedSize, " ");
                difference = !actualIterator.hasNext();
            } else {
                pad(sb, 0, largestExpectedSize, " ");
                difference = true;
            }
            if (actualIterator.hasNext()) {
                String actual = actualIterator.next();
                difference = difference || !actual.equals(expected);
                if (difference) {
                    sb.append(" <|> ");
                } else {
                    sb.append("  |  ");
                }
                sb.append(actual);
            } else {
                sb.append(" <|> ");
            }

            sb.append(NEWLINE);
        }
    }

    private void pad(StringBuilder sb, int currentLength, int targetLength, String character) {
        for (int t = currentLength; t < targetLength; t++) {
            sb.append(character);
        }
    }
}

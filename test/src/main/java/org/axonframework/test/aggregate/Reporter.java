/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.hamcrest.Description;
import org.hamcrest.StringDescription;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reporter generates extensive human readable reports of what the expected outcome of a test was, and what the
 * actual results were.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class Reporter {

    private static final String NEWLINE = System.getProperty("line.separator");

    /**
     * Report an error in the ordering or count of events. This is typically a difference that can be shown to the user
     * by enumerating the expected and actual events
     *
     * @param actualEvents   The events that were found
     * @param expectedEvents The events that were expected
     * @param probableCause  An optional exception that might be the reason for wrong events
     */
    public void reportWrongEvent(Collection<?> actualEvents, Collection<?> expectedEvents,
                                 Throwable probableCause) {
        StringBuilder sb = new StringBuilder(
                "The published events do not match the expected events");
        appendEventOverview(sb, expectedEvents, actualEvents);
        appendProbableCause(probableCause, sb);

        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error in the ordering or count of events. This is typically a difference that can be shown to the user
     * by enumerating the expected and actual events
     *
     * @param actualEvents  The events that were found
     * @param expectation   A Description of what was expected
     * @param probableCause An optional exception that might be the reason for wrong events
     */
    public void reportWrongEvent(Collection<?> actualEvents, StringDescription expectation, Throwable probableCause) {
        StringBuilder sb = new StringBuilder(
                "The published events do not match the expected events.");
        sb.append("Expected :");
        sb.append(NEWLINE);
        sb.append(expectation);
        sb.append(NEWLINE);
        sb.append("But got");
        if (actualEvents.isEmpty()) {
            sb.append(" none");
        } else {
            sb.append(":");
        }
        for (Object publishedEvent : actualEvents) {
            sb.append(NEWLINE);
            sb.append(publishedEvent.getClass().getSimpleName());
            sb.append(": ");
            sb.append(publishedEvent.toString());
        }
        appendProbableCause(probableCause, sb);

        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Reports an error due to an unexpected exception. This means a return value was expected, but an exception was
     * thrown by the command handler
     *
     * @param actualException The actual exception
     * @param expectation     A text describing what was expected
     */
    public void reportUnexpectedException(Throwable actualException, Description expectation) {
        StringBuilder sb = new StringBuilder("The command handler threw an unexpected exception");
        sb.append(NEWLINE)
          .append(NEWLINE)
          .append("Expected <") //NOSONAR
          .append(expectation.toString())
          .append("> but got <exception of type [")
          .append(actualException.getClass().getSimpleName())
          .append("]>. Stack trace follows:")
          .append(NEWLINE);
        writeStackTrace(actualException, sb);
        sb.append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Reports an error due to a wrong return value.
     *
     * @param actual      The actual description / return value
     * @param expectation The expected description / return value
     */
    public void reportWrongResult(Object actual, Object expectation) {
        StringBuilder sb = new StringBuilder("The command handler returned an unexpected value");
        sb.append(NEWLINE)
          .append(NEWLINE)
          .append("Expected <"); //NOSONAR
        sb.append(expectation.toString());
        sb.append("> but got <");
        describe(actual, sb);
        sb.append(">")
          .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to an unexpected return value, while an exception was expected.
     *
     * @param actualReturnValue The actual return value
     * @param description       A description describing the expected value
     */
    public void reportUnexpectedReturnValue(Object actualReturnValue, Description description) {
        StringBuilder sb = new StringBuilder("The command handler returned normally, but an exception was expected");
        sb.append(NEWLINE)
          .append(NEWLINE)
          .append("Expected <"); //NOSONAR
        sb.append(description.toString());
        sb.append("> but returned with <");
        describe(actualReturnValue, sb);
        sb.append(">")
          .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to a an exception of an unexpected type.
     *
     * @param actualException The actual exception
     * @param description     A description describing the expected value
     */
    public void reportWrongException(Throwable actualException, Description description) {
        StringBuilder sb = new StringBuilder("The command handler threw an exception, but not of the expected type")
                .append(NEWLINE)
                .append(NEWLINE)
                .append("Expected <") //NOSONAR
                .append(description.toString())
                .append("> but got <exception of type [")
                .append(actualException.getClass().getSimpleName())
                .append("]>. Stacktrace follows: ")
                .append(NEWLINE);
        writeStackTrace(actualException, sb);
        sb.append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to a difference in exception message.
     *
     * @param actualException The actual exception
     * @param description     A description describing the expected value
     */
    public void reportWrongExceptionMessage(Throwable actualException, Description description) {
        throw new AxonAssertionError("The command handler threw an exception, but not with expected message"
                                             + NEWLINE +
                                             NEWLINE +
                                             "Expected <" + //NOSONAR
                                             description.toString() +
                                             "> but got <message [" +
                                             actualException.getMessage() +
                                             "]>." +
                                             NEWLINE +
                                             NEWLINE);
    }

    /**
     * Report an error due to a difference in one of the fields of the message payload.
     *
     * @param messageType The (runtime) type of the message the difference was found in
     * @param field       The field that contains the difference
     * @param actual      The actual value of the field
     * @param expected    The expected value of the field
     */
    public void reportDifferentPayloads(Class<?> messageType, Field field, Object actual,
                                        Object expected) {
        StringBuilder sb = new StringBuilder("One of the messages contained different values than expected");
        sb.append(NEWLINE)
          .append(NEWLINE)
          .append("In a message of type [")
          .append(messageType.getSimpleName())
          .append("], the property [")
          .append(field.getName())
          .append("] ");
        if (!messageType.equals(field.getDeclaringClass())) {
            sb.append("(declared in [")
              .append(field.getDeclaringClass().getSimpleName())
              .append("]) ");
        }

        sb.append("was not as expected.")
          .append(NEWLINE)
          .append("Expected <") //NOSONAR
          .append(nullSafeToString(expected))
          .append("> but got <")
          .append(nullSafeToString(actual))
          .append(">")
          .append(NEWLINE);
        throw new AxonAssertionError(sb.toString());
    }

    /**
     * Report an error due to a difference in the metadata of a message
     *
     * @param messageType       The (runtime) type of the message the difference was found in
     * @param missingEntries    The expected key-value pairs that where not present in the metadata
     * @param additionalEntries Key-value pairs that where present in the metadata but not expected
     */
    public void reportDifferentMetaData(Class<?> messageType, Map<String, Object> missingEntries,
                                        Map<String, Object> additionalEntries) {
        StringBuilder sb = new StringBuilder("One of the messages contained different metadata than expected");
        sb.append(NEWLINE)
          .append(NEWLINE)
          .append("In a message of type [")
          .append(messageType.getSimpleName())
          .append("], ");
        if (!additionalEntries.isEmpty()) {
            sb.append("metadata entries" + NEWLINE).append("[");
            for (Map.Entry<String, Object> entry : additionalEntries.entrySet()) {
                sb.append(entryAsString(entry) + ", ");
            }
            sb.delete(sb.lastIndexOf(", "), sb.lastIndexOf(",") + 2);
            sb.append("] " + NEWLINE);
            sb.append("were not expected. ");
        }
        if (!missingEntries.isEmpty()) {
            sb.append("metadata entries " + NEWLINE).append("[");
            for (Map.Entry<String, Object> entry : missingEntries.entrySet()) {
                sb.append(entryAsString(entry) + ", ");
            }
            sb.delete(sb.lastIndexOf(","), sb.lastIndexOf(",") + 2);
            sb.append("] " + NEWLINE);
            sb.append("were expected but not seen.");
        }
        throw new AxonAssertionError(sb.toString());
    }

    private void appendProbableCause(Throwable probableCause, StringBuilder sb) {
        if (probableCause != null) {
            sb.append(NEWLINE);
            sb.append("A probable cause for the wrong chain of events is an "
                              + "exception that occurred while handling the command.");
            sb.append(NEWLINE);
            CharArrayWriter charArrayWriter = new CharArrayWriter();
            probableCause.printStackTrace(new PrintWriter(charArrayWriter));
            sb.append(charArrayWriter.toCharArray());
        }
    }

    private void writeStackTrace(Throwable actualException, StringBuilder sb) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);
        actualException.printStackTrace(pw);
        pw.flush();
        sb.append(new String(baos.toByteArray()));
    }

    private String nullSafeToString(final Object value) {
        if (value == null) {
            return "<null>";
        }
        return value.toString();
    }

    private void describe(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else {
            sb.append(value.toString());
        }
    }

    private String entryAsString(Map.Entry<?, ?> entry) {
        if (entry == null) {
            return "<null>=<null>";
        } else {
            return nullSafeToString(entry.getKey()) + "=" + nullSafeToString(entry.getValue());
        }
    }

    private void appendEventOverview(StringBuilder sb, Collection<?> leftColumnEvents,
                                     Collection<?> rightColumnEvents) {
        List<String> actualTypes = new ArrayList<>(rightColumnEvents.size());
        List<String> expectedTypes = new ArrayList<>(leftColumnEvents.size());
        int largestExpectedSize = 8;
        actualTypes.addAll(rightColumnEvents.stream().map((Function<Object, String>) this::payloadContentType)
                                            .collect(Collectors.toList()));
        for (Object event : leftColumnEvents) {
            String simpleName = payloadContentType(event);
            if (simpleName.length() > largestExpectedSize) {
                largestExpectedSize = simpleName.length();
            }
            expectedTypes.add(simpleName);
        }
        sb.append(NEWLINE);
        sb.append(NEWLINE);
        sb.append("Expected");
        pad(sb, "Expected".length(), largestExpectedSize, " ");
        sb.append("  |  ")
          .append("Actual")
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

    private String payloadContentType(Object event) {
        String simpleName;
        if (EventMessage.class.isInstance(event)) {
            simpleName = ((EventMessage) event).getPayload().getClass().getName();
        } else {
            simpleName = event.getClass().getName();
        }
        return simpleName;
    }

    private void pad(StringBuilder sb, int currentLength, int targetLength, String character) {
        for (int t = currentLength; t < targetLength; t++) {
            sb.append(character);
        }
    }
}

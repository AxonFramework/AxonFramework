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

package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.EqualFieldsMatcher;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static java.lang.String.format;
import static org.axonframework.test.saga.DescriptionUtils.describe;

/**
 * Helper class for validation of dispatched commands.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class CommandValidator {

    private final RecordingCommandBus commandBus;
    private final FieldFilter fieldFilter;

    /**
     * Creates a validator which monitors the given <code>commandBus</code>.
     *
     * @param commandBus  the command bus to monitor
     * @param fieldFilter the filter describing the Fields to include in a comparison
     */
    public CommandValidator(RecordingCommandBus commandBus, FieldFilter fieldFilter) {
        this.commandBus = commandBus;
        this.fieldFilter = fieldFilter;
    }

    /**
     * Starts recording commands on the command bus.
     */
    public void startRecording() {
        commandBus.clearCommands();
    }

    /**
     * Assert that the given commands have been dispatched in the exact sequence provided.
     *
     * @param expected The commands expected to have been published on the bus
     */
    public void assertDispatchedEqualTo(Object... expected) {
        List<CommandMessage<?>> actual = commandBus.getDispatchedCommands();
        if (actual.size() != expected.length) {
            throw new AxonAssertionError(format(
                    "Got wrong number of commands dispatched. Expected <%s>, got <%s>",
                    expected.length,
                    actual.size()));
        }
        Iterator<CommandMessage<?>> actualIterator = actual.iterator();
        Iterator<Object> expectedIterator = Arrays.asList(expected).iterator();

        int counter = 0;
        while (actualIterator.hasNext()) {
            CommandMessage<?> actualItem = actualIterator.next();
            Object expectedItem = expectedIterator.next();
            if (expectedItem instanceof CommandMessage) {
                CommandMessage<?> expectedMessage = (CommandMessage<?>) expectedItem;
                if (!expectedMessage.getPayloadType().equals(actualItem.getPayloadType())) {
                    throw new AxonAssertionError(format(
                            "Unexpected payload type of command at position %s (0-based). Expected <%s>, got <%s>",
                            counter,
                            expectedMessage.getPayloadType(),
                            actualItem.getPayloadType()));
                }
                assertCommandEquality(counter, expectedMessage.getPayload(), actualItem.getPayload());
                if (!expectedMessage.getMetaData().equals(actualItem.getMetaData())) {
                    throw new AxonAssertionError(format(
                            "Unexpected Meta Data of command at position %s (0-based). Expected <%s>, got <%s>",
                            counter,
                            expectedMessage.getMetaData(),
                            actualItem.getMetaData()));
                }
            } else {
                assertCommandEquality(counter, expectedItem, actualItem.getPayload());
            }
            counter++;
        }
    }

    /**
     * Assert that commands matching the given <code>matcher</code> has been dispatched on the command bus.
     *
     * @param matcher The matcher validating the actual commands
     */
    public void assertDispatchedMatching(Matcher<?> matcher) {
        if (!matcher.matches(commandBus.getDispatchedCommands())) {
            Description expectedDescription = new StringDescription();
            Description actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(commandBus.getDispatchedCommands(), actualDescription);
            throw new AxonAssertionError(format("Incorrect dispatched command. Expected <%s>, but got <%s>",
                                                expectedDescription, actualDescription));
        }
    }

    private void assertCommandEquality(int commandIndex, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            if (!expected.getClass().equals(actual.getClass())) {
                throw new AxonAssertionError(format("Wrong command type at index %s (0-based). "
                                                            + "Expected <%s>, but got <%s>",
                                                    commandIndex,
                                                    expected.getClass().getSimpleName(),
                                                    actual.getClass().getSimpleName()));
            }
            EqualFieldsMatcher<Object> matcher = new EqualFieldsMatcher<Object>(expected, fieldFilter);
            if (!matcher.matches(actual)) {
                throw new AxonAssertionError(format("Unexpected command at index %s (0-based). "
                                                            + "Field value of '%s.%s', expected <%s>, but got <%s>",
                                                    commandIndex,
                                                    expected.getClass().getSimpleName(),
                                                    matcher.getFailedField().getName(),
                                                    matcher.getFailedFieldExpectedValue(),
                                                    matcher.getFailedFieldActualValue()));
            }
        }
    }
}

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

package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import static org.axonframework.test.saga.DescriptionUtils.describe;

/**
 * Helper class for validation of dispatched commands.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class CommandValidator {

    private final FieldFilter fieldFilter;
    private final Supplier<List<CommandMessage<?>>> dispatchedCommands;
    private final Runnable clearRecordedCommands;

    /**
     * Creates a validator which monitors the given {@code commandBus}.
     *
     * @param commandBus  the command bus to monitor
     * @param fieldFilter the filter describing the Fields to include in a comparison
     */
    public CommandValidator(RecordingCommandBus commandBus, FieldFilter fieldFilter) {
        this(commandBus::getDispatchedCommands, commandBus::clearCommands, fieldFilter);
    }

    public CommandValidator(
            Supplier<List<CommandMessage<?>>> dispatchedCommands,
            Runnable clearRecordedCommands,
            FieldFilter fieldFilter
    ) {
        this.dispatchedCommands = dispatchedCommands;
        this.clearRecordedCommands = clearRecordedCommands;
        this.fieldFilter = fieldFilter;
    }

    /**
     * Starts recording commands on the command bus.
     */
    public void startRecording() {
        clearRecordedCommands.run();
    }

    /**
     * Assert that the given commands have been dispatched in the exact sequence provided.
     *
     * @param expected The commands expected to have been published on the bus
     */
    public void assertDispatchedEqualTo(Object... expected) {
        List<CommandMessage<?>> actual = dispatchedCommands.get();
        if (actual.size() != expected.length) {
            throw new AxonAssertionError(
                    "Got wrong number of commands dispatched.\n"
                            + "Expected <" + expected.length + ">,\n but got <" + actual.size() + ">."
            );
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
                    throw new AxonAssertionError(
                            "Unexpected payload type of command at position " + counter + " (0-based).\n"
                                    + "Expected <" + expectedMessage.getPayloadType() + ">,\n"
                                    + " but got <" + actualItem.getPayloadType() + ">."
                    );
                }
                assertCommandEquality(counter, expectedMessage.payload(), actualItem.payload());
                if (!expectedMessage.getMetaData().equals(actualItem.getMetaData())) {
                    throw new AxonAssertionError(
                            "Unexpected Meta Data of command at position " + counter + " (0-based).\n"
                                    + "Expected <" + expectedMessage.getMetaData() + ">,\n"
                                    + " but got <" + actualItem.getMetaData() + ">."
                    );
                }
            } else {
                assertCommandEquality(counter, expectedItem, actualItem.payload());
            }
            counter++;
        }
    }

    /**
     * Assert that commands matching the given {@code matcher} has been dispatched on the command bus.
     *
     * @param matcher The matcher validating the actual commands
     */
    public void assertDispatchedMatching(Matcher<?> matcher) {
        if (!matcher.matches(dispatchedCommands.get())) {
            Description expectedDescription = new StringDescription();
            Description actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(dispatchedCommands.get(), actualDescription);
            throw new AxonAssertionError(
                    "Incorrect dispatched command.\n"
                            + "Expected <" + expectedDescription + ">,\n but got <" + actualDescription + ">."
            );
        }
    }

    private void assertCommandEquality(int commandIndex, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            if (!expected.getClass().equals(actual.getClass())) {
                throw new AxonAssertionError(
                        "Wrong command type at index " + commandIndex + " (0-based).\n"
                                + "Expected <" + expected.getClass().getSimpleName() + ">,\n"
                                + " but got <" + actual.getClass().getSimpleName() + ">."
                );
            }
            Matcher<Object> matcher = Matchers.deepEquals(expected, fieldFilter);
            if (!matcher.matches(actual)) {
                throw new AxonAssertionError(
                        "Unexpected command at index " + commandIndex + " (0-based).\n"
                                + "Expected <" + expected + ">,\n but got <" + actual + ">."
                );
            }
        }
    }
}

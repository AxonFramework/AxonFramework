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
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.matchers.EqualFieldsMatcher;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import static java.lang.String.format;
import static org.axonframework.test.saga.DescriptionUtils.describe;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Helper class for validation of dispatched commands.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class CommandValidator {

    private final RecordingCommandBus commandBus;
    private final FieldFilter fieldFilter;
    public final static String REASON_SIZE_MISMATCH = "Number of commands dispatched is equal.";
    public final static String REASON_COMMAND_MISMATCH = "Expected command is not present in actual list.";

    /**
     * Creates a validator with {@link AllFieldsFilter} which monitors the given <code>commandBus</code>.
     *
     * @param commandBus the command bus to monitor
     */
    public CommandValidator(RecordingCommandBus commandBus) {
        this(commandBus, AllFieldsFilter.instance());
    }

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
    public void assertDispatchedEqualTo(Object... expected) throws AxonAssertionError {
        try {
            assertThat(CommandValidator.REASON_SIZE_MISMATCH,
                    commandBus.getDispatchedCommands().size(),
                    Matchers.equalTo(expected.length));
            assertThat(CommandValidator.REASON_COMMAND_MISMATCH,
                    commandBus.getDispatchedCommands(),
                    Matchers.payloadsMatching(Matchers.exactSequenceOf(equalsTo(expected))));
        } catch (AssertionError e) {
            throw new AxonAssertionError(e.getMessage());
        }
    }

    /**
     * Assert that the given commands have been dispatched ignoring the exact sequence provided.
     *
     * @param expected The commands expected to have been published on the bus
     */
    public void assertDispatchedEqualToIgnoringSequence(Object... expected) throws AxonAssertionError {
        try {
            assertThat(CommandValidator.REASON_SIZE_MISMATCH,
                    commandBus.getDispatchedCommands().size(),
                    Matchers.equalTo(expected.length));
            assertThat(CommandValidator.REASON_COMMAND_MISMATCH,
                    commandBus.getDispatchedCommands(),
                    Matchers.payloadsMatching(Matchers.listWithAllOf(equalsTo(expected))));
        } catch (AssertionError e) {
            throw new AxonAssertionError(e.getMessage());
        }
    }

    private Matcher[] equalsTo(Object... expected) {
        Matcher[] expectedCommands = new EqualFieldsMatcher[expected.length];
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] instanceof CommandMessage) {
                CommandMessage msg = (CommandMessage) expected[i];
                expected[i] = msg.getPayload();
            }
            expectedCommands[i] = Matchers.equalTo(expected[i], fieldFilter);
        }
        return expectedCommands;
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
}

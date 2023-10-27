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

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.command.Command;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all the functions provided on the {@link GrpcBackedCommandMessage}. The {@link Command} to be passed to a
 * GrpcBackedCommandMessage is created by using the {@link CommandSerializer}.
 *
 * @author Steven van Beelen
 */
class GrpcBackedCommandMessageTest {

    private static final TestCommand TEST_COMMAND = new TestCommand("aggregateId", 42);
    private static final String ROUTING_KEY = "someRoutingKey";
    private static final int PRIORITY = 1;

    private final Serializer serializer = TestSerializer.xStreamSerializer();
    private final CommandSerializer commandSerializer =
            new CommandSerializer(serializer, new AxonServerConfiguration());

    @Test
    void getCommandNameReturnsTheNameOfTheCommandAsSpecifiedInTheGrpcCommand() {
        CommandMessage<TestCommand> testCommandMessage = GenericCommandMessage.asCommandMessage(TEST_COMMAND);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        assertEquals(testCommand.getName(), testSubject.getCommandName());
    }

    @Test
    void getIdentifierReturnsTheSameIdentifierAsSpecifiedInTheGrpcCommand() {
        CommandMessage<TestCommand> testCommandMessage = GenericCommandMessage.asCommandMessage(TEST_COMMAND);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        assertEquals(testCommand.getMessageIdentifier(), testSubject.getIdentifier());
    }

    @Test
    void getMetaDataReturnsTheSameMapAsWasInsertedInTheGrpcCommand() {
        MetaData expectedMetaData = MetaData.with("some-key", "some-value");
        CommandMessage<TestCommand> testCommandMessage =
                GenericCommandMessage.<TestCommand>asCommandMessage(TEST_COMMAND).withMetaData(expectedMetaData);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        assertEquals(expectedMetaData, testSubject.getMetaData());
    }

    @Test
    void getPayloadReturnsAnIdenticalObjectAsInsertedThroughTheGrpcCommand() {
        TestCommand expectedCommand = TEST_COMMAND;
        CommandMessage<TestCommand> testCommandMessage = GenericCommandMessage.asCommandMessage(expectedCommand);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        assertEquals(expectedCommand, testSubject.getPayload());
    }

    @Test
    void getPayloadTypeReturnsTheTypeOfTheInsertedCommand() {
        CommandMessage<TestCommand> testCommandMessage = GenericCommandMessage.asCommandMessage(TEST_COMMAND);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        assertEquals(TestCommand.class, testSubject.getPayloadType());
    }

    @Test
    void withMetaDataCompletelyReplacesTheInitialMetaDataMap() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        CommandMessage<TestCommand> testCommandMessage =
                GenericCommandMessage.<TestCommand>asCommandMessage(TEST_COMMAND).withMetaData(testMetaData);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        MetaData replacementMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetaData(replacementMetaData);
        MetaData resultMetaData = testSubject.getMetaData();
        assertFalse(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertEquals(replacementMetaData, resultMetaData);
    }

    @Test
    void andMetaDataAppendsToTheExistingMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        CommandMessage<TestCommand> testCommandMessage =
                GenericCommandMessage.<TestCommand>asCommandMessage(TEST_COMMAND).withMetaData(testMetaData);
        Command testCommand = commandSerializer.serialize(testCommandMessage, ROUTING_KEY, PRIORITY);
        GrpcBackedCommandMessage<TestCommand> testSubject = new GrpcBackedCommandMessage<>(testCommand, serializer);

        MetaData additionalMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetaData(additionalMetaData);
        MetaData resultMetaData = testSubject.getMetaData();

        assertTrue(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertTrue(resultMetaData.containsKey(additionalMetaData.keySet().iterator().next()));
    }

    private static class TestCommand {

        private final String aggregateId;
        private final int someValue;

        private TestCommand(String aggregateId, int someValue) {
            this.aggregateId = aggregateId;
            this.someValue = someValue;
        }

        @SuppressWarnings("unused")
        public String getAggregateId() {
            return aggregateId;
        }

        @SuppressWarnings("unused")
        public int getSomeValue() {
            return someValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestCommand that = (TestCommand) o;
            return someValue == that.someValue &&
                    Objects.equals(aggregateId, that.aggregateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregateId, someValue);
        }
    }
}
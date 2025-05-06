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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ConstructorBasedEventSourcedEntityFactoryTest {

    @Test
    void nullEntityTypeThrowsException() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new ConstructorBasedEventSourcedEntityFactory<>(null));
    }

    @Nested
    public class WithFirstEventMessage {

        @Test
        void entityWithAllTypesOfConstructorWillBeConstructedWithPayload() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithAllConstructors.class);
            TestEntityPayload testEntityPayload = new TestEntityPayload();
            GenericEventMessage<TestEntityPayload> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityPayload.class), testEntityPayload);

            TestEntityWithAllConstructors createdEntity = subject
                    .createEntityBasedOnFirstEventMessage(new TestEntityIdentifier("testId"), eventMessage);

            assertSame(testEntityPayload, createdEntity.payload);
            assertNull(createdEntity.eventMessage);
        }

        @Test
        void entityWithEventMessageAndIdConstructorWillBeConstructedWithEventMessage() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithEventMessageAndIdConstructor.class);
            TestEntityPayload testEntityPayload = new TestEntityPayload();
            GenericEventMessage<TestEntityPayload> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityIdentifier.class), testEntityPayload);

            TestEntityWithEventMessageAndIdConstructor createdEntity = subject
                    .createEntityBasedOnFirstEventMessage(testEntityPayload, eventMessage);

            assertSame(eventMessage, createdEntity.eventMessage);
        }

        @Test
        void entityWithPayloadAndZeroArgConstructorWillBeConstructedWithPayload() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithPayloadAndZeroArgConstructor.class);
            TestEntityPayload testEntityPayload = new TestEntityPayload();
            GenericEventMessage<TestEntityPayload> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityPayload.class), testEntityPayload);

            TestEntityWithPayloadAndZeroArgConstructor createdEntity = subject
                    .createEntityBasedOnFirstEventMessage(new TestEntityIdentifier("testId"), eventMessage);

            assertSame(testEntityPayload, createdEntity.payload);
        }

        @Test
        void entityWithPayloadAndIdConstructorWillBeConstructedWithPayload() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithPayloadAndIdConstructor.class);
            TestEntityPayload testEntityPayload = new TestEntityPayload();
            GenericEventMessage<TestEntityPayload> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityPayload.class), testEntityPayload);

            TestEntityWithPayloadAndIdConstructor createdEntity = subject
                    .createEntityBasedOnFirstEventMessage(new TestEntityIdentifier("testId"), eventMessage);

            assertSame(testEntityPayload, createdEntity.payload);
        }

        @Test
        void entityWithIdAndDefaultConstructorWillBeConstructedWithId() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithIdAndDefaultConstructor.class);
            TestEntityIdentifier testEntityIdentifier = new TestEntityIdentifier("testId");
            GenericEventMessage<TestEntityIdentifier> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityIdentifier.class), testEntityIdentifier);

            TestEntityWithIdAndDefaultConstructor createdEntity = subject
                    .createEntityBasedOnFirstEventMessage(testEntityIdentifier, eventMessage);

            assertSame(testEntityIdentifier, createdEntity.identifier);
        }

        @Test
        void entityWithOnlyZeroArgConstructorWillBeConstructedWithoutArguments() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithOnlyZeroArgConstructor.class);
            TestEntityIdentifier testEntityIdentifier = new TestEntityIdentifier("testId");
            GenericEventMessage<TestEntityIdentifier> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityIdentifier.class), testEntityIdentifier);

            TestEntityWithOnlyZeroArgConstructor createdEntity = subject
                    .createEntityBasedOnFirstEventMessage(testEntityIdentifier, eventMessage);

            assertNotNull(createdEntity);
        }

        @Test
        void entityWithWrongConstructorWillThrowException() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithWrongConstructor.class);
            TestEntityIdentifier testEntityIdentifier = new TestEntityIdentifier("testId");
            GenericEventMessage<TestEntityIdentifier> eventMessage = new GenericEventMessage<>(new MessageType(
                    TestEntityIdentifier.class), testEntityIdentifier);

            assertThrows(RuntimeException.class, () -> subject.createEntityBasedOnFirstEventMessage(testEntityIdentifier, eventMessage));
        }
    }

    @Nested
    public class WithoutFirstEventMessage {

        @Test
        void entityWithAllTypesOfConstructorWillBeConstructedWithId() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithAllConstructors.class);
            TestEntityIdentifier testEntityIdentifier = new TestEntityIdentifier("testId");

            TestEntityWithAllConstructors createdEntity = subject
                    .createEmptyEntity(testEntityIdentifier);

            assertSame(testEntityIdentifier, createdEntity.identifier);
        }

        @Test
        void entityWithIdAndDefaultConstructorWillBeConstructedWithId() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithIdAndDefaultConstructor.class);
            TestEntityIdentifier testEntityIdentifier = new TestEntityIdentifier("testId");

            TestEntityWithIdAndDefaultConstructor createdEntity = subject
                    .createEmptyEntity(testEntityIdentifier);

            assertSame(testEntityIdentifier, createdEntity.identifier);
        }

        @Test
        void entityWithWrongConstructorWillThrowException() {
            var subject = new ConstructorBasedEventSourcedEntityFactory<>(TestEntityWithWrongConstructor.class);
            TestEntityIdentifier testEntityIdentifier = new TestEntityIdentifier("testId");

            assertThrows(RuntimeException.class, () -> subject.createEmptyEntity(testEntityIdentifier));
        }
    }

    private static class TestEntityWithAllConstructors {

        public EventMessage<?> eventMessage;
        public TestEntityPayload payload;
        public TestEntityIdentifier identifier;

        private TestEntityWithAllConstructors(EventMessage<?> eventMessage) {
            this.eventMessage = eventMessage;
        }

        public TestEntityWithAllConstructors(TestEntityPayload payload) {
            this.payload = payload;
        }

        public TestEntityWithAllConstructors(TestEntityIdentifier identifier) {
            this.identifier = identifier;
        }

        public TestEntityWithAllConstructors() {
            // Default constructor
        }
    }



    private static class TestEntityWithEventMessageAndIdConstructor {

        public EventMessage<?> eventMessage;
        public TestEntityIdentifier identifier;

        public TestEntityWithEventMessageAndIdConstructor(EventMessage<?> eventMessage) {
            this.eventMessage = eventMessage;
        }

        public TestEntityWithEventMessageAndIdConstructor(TestEntityIdentifier identifier) {
            this.identifier = identifier;
        }
    }


    private static class TestEntityWithPayloadAndZeroArgConstructor {

        public TestEntityPayload payload;

        public TestEntityWithPayloadAndZeroArgConstructor(TestEntityPayload payload) {
            this.payload = payload;
        }

        public TestEntityWithPayloadAndZeroArgConstructor() {
            // Default constructor
        }
    }

    private static class TestEntityWithPayloadAndIdConstructor {

        public TestEntityPayload payload;
        public TestEntityIdentifier identifier;

        public TestEntityWithPayloadAndIdConstructor(TestEntityPayload payload) {
            this.payload = payload;
        }

        public TestEntityWithPayloadAndIdConstructor(TestEntityIdentifier identifier) {
            this.identifier = identifier;
        }
    }

    private static class TestEntityWithIdAndDefaultConstructor {

        public TestEntityIdentifier identifier;

        public TestEntityWithIdAndDefaultConstructor(TestEntityIdentifier identifier) {
            this.identifier = identifier;
        }

        public TestEntityWithIdAndDefaultConstructor() {
            // Default constructor
        }
    }

    private static class TestEntityWithOnlyZeroArgConstructor {

        public TestEntityWithOnlyZeroArgConstructor() {
            // Default constructor
        }
    }

    private static class TestEntityWithWrongConstructor {

        public TestEntityWithWrongConstructor(Integer argument) {
            // Constructor with wrong parameters
        }
    }


    private static class TestEntityPayload {

    }

    private static class TestEntityIdentifier {

        private final String identifier;

        public TestEntityIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }
}
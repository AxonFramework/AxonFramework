/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.annotation;

import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.EntityIdResolutionException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBasedEntityIdResolverTest {

    private final AnnotationBasedEntityIdResolver<Object> testSubject = new AnnotationBasedEntityIdResolver<>();

    @Test
    void resolvesIdOfSingleTargetCommand() throws EntityIdResolutionException {
        // given
        SingleTargetCommand command = new SingleTargetCommand("id-2792793");
        var message = new GenericCommandMessage(new MessageType(command.getClass()), command);

        // when
        Object result = testSubject.resolve(message, StubProcessingContext.forMessage(message));

        // then
        Assertions.assertEquals("id-2792793", result);
    }

    @Test
    void resolvesIdOfSingleTargetCommandWithGetterAnnotated() throws EntityIdResolutionException {
        // given
        SingleTargetGetterCommand command = new SingleTargetGetterCommand("id-2792794");
        var message = new GenericCommandMessage(new MessageType(command.getClass()), command);

        // when
        Object result = testSubject.resolve(message, StubProcessingContext.forMessage(message));

        // then
        Assertions.assertEquals("id-2792794", result);
    }

    @Test
    void resolvesIdOfSingleTargetCommandWithRecord() throws EntityIdResolutionException {
        // given
        SingleTargetRecordCommand command = new SingleTargetRecordCommand("id-2792795");
        var message = new GenericCommandMessage(new MessageType(command.getClass()), command);

        // when
        Object result = testSubject.resolve(message, StubProcessingContext.forMessage(message));

        // then
        Assertions.assertEquals("id-2792795", result);
    }

    @Test
    void throwsExceptionWhenMultipleTargetAnnotationsArePresentThatDontMatch() {
        // given
        MultipleTargetCommand command = new MultipleTargetCommand("id-2792796", "id-2792797");
        var message = new GenericCommandMessage(new MessageType(command.getClass()), command);

        // then
        assertThrows(EntityIdResolutionException.class, () -> testSubject.resolve(
                message, StubProcessingContext.forMessage(message)));
    }

    @Test
    void resolvesNonNullIdWhenOnlyOneTargedIdFieldIsNonNull() throws EntityIdResolutionException {
        // given
        MultipleTargetCommand command = new MultipleTargetCommand("id-2792798", null);

        // when
        var message = new GenericCommandMessage(new MessageType(command.getClass()), command);
        Object result = testSubject.resolve(message, StubProcessingContext.forMessage(message));

        // then
        Assertions.assertEquals("id-2792798", result);
    }

    @Test
    void resolvesNonNullIdWhenAllTargetIdFieldsHaveSameValue() throws EntityIdResolutionException {
        // given
        MultipleTargetCommand command = new MultipleTargetCommand("id-2792700", "id-2792700");
        var message = new GenericCommandMessage(new MessageType(command.getClass()), command);

        // when
        Object result = testSubject.resolve(message, StubProcessingContext.forMessage(message));

        // then
        Assertions.assertEquals("id-2792700", result);
    }

    @Test
    void throwsEntityIdResolutionExceptionWhenNoTargetAnnotationPresent() {
        // given
        NoTargetCommand command = new NoTargetCommand();
        GenericCommandMessage commandMessage = new GenericCommandMessage(
                new MessageType(command.getClass()), command
        );

        // when & then
        assertThrows(EntityIdResolutionException.class,
                     () -> testSubject.resolve(commandMessage, StubProcessingContext.forMessage(commandMessage)));
    }

    static class SingleTargetCommand {

        @TargetEntityId
        private final String targetId;

        SingleTargetCommand(String targetId) {
            this.targetId = targetId;
        }

        public String getTargetId() {
            return targetId;
        }
    }


    static class SingleTargetGetterCommand {

        @TargetEntityId
        private final String targetId;

        SingleTargetGetterCommand(String targetId) {
            this.targetId = targetId;
        }

        @TargetEntityId
        public String getTargetId() {
            return targetId;
        }
    }

    record SingleTargetRecordCommand(@TargetEntityId String targetId) {

    }

    static class MultipleTargetCommand {

        @TargetEntityId
        private final String targetId;
        @TargetEntityId
        private final String targetId2;

        MultipleTargetCommand(String targetId, String targetId2) {
            this.targetId = targetId;
            this.targetId2 = targetId2;
        }

        public String getTargetId() {
            return targetId;
        }

        public String getTargetId2() {
            return targetId2;
        }
    }

    static class NoTargetCommand {

    }
}
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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.commandhandling.annotation.RoutingKey;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.entity.child.EventTargetMatcher;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingKeyEventTargetMatcherDefinitionTest {

    private final RoutingKeyEventTargetMatcherDefinition definition = new RoutingKeyEventTargetMatcherDefinition();


    @Test
    void allowsNoRoutingKeyOnSingleValueEntityMember() throws NoSuchFieldException {
        AnnotatedEntityModel<ChildEntityWithoutRoutingKey> childEntityModel = mock(AnnotatedEntityModel.class);
        when(childEntityModel.entityType()).thenReturn(ChildEntityWithoutRoutingKey.class);
        EventTargetMatcher<ChildEntityWithoutRoutingKey> result = definition.createChildEntityMatcher(childEntityModel,
                                                                                                      SimpleSingleChildValueEntity.class.getDeclaredField(
                                                                                                              "child"));

        assertNotNull(result, "Expected a non-null EventTargetMatcher");
    }

    @Test
    void doesNotAllowMissingRoutingKeyOnCollectionTypeMember() {
        AnnotatedEntityModel<ChildEntityWithoutRoutingKey> childEntityModel = mock(AnnotatedEntityModel.class);
        when(childEntityModel.entityType()).thenReturn(ChildEntityWithoutRoutingKey.class);
        assertThrows(AxonConfigurationException.class, () -> definition.createChildEntityMatcher(
                             childEntityModel,
                             SimpleMultiChildValueEntity.class.getDeclaredField("child")),
                     "Expected IllegalArgumentException when no routing key is present on a collection type member");
    }

    class SimpleSingleChildValueEntity {

        @EntityMember
        private ChildEntityWithoutRoutingKey child;
    }


    class SimpleMultiChildValueEntity {

        @EntityMember
        private List<ChildEntityWithoutRoutingKey> child;
    }

    class ChildEntityWithoutRoutingKey {

    }

    @Test
    void doesNotAllowMissingRoutingKeyOnMessage() throws NoSuchFieldException {
        AnnotatedEntityModel<ChildEntityWithWrongRoutingKey> childEntityModel = mock(AnnotatedEntityModel.class);
        when(childEntityModel.entityType()).thenReturn(ChildEntityWithWrongRoutingKey.class);

        EventTargetMatcher<ChildEntityWithWrongRoutingKey> resolver = definition.createChildEntityMatcher(
                childEntityModel,
                ParentEntityWithWrongRoutingKeyChild.class.getDeclaredField("child"));

        MessageType messageType = new MessageType(MyCommandPayload.class);
        when(childEntityModel.getExpectedRepresentation(messageType.qualifiedName())).thenReturn((Class) MyCommandPayload.class);

        assertThrows(UnknownRoutingKeyException.class, () -> {
            resolver.matches(
                    new ChildEntityWithWrongRoutingKey(),
                    new GenericEventMessage<>(messageType, new MyCommandPayload("someValue")),
                    new StubProcessingContext()
            );
        });
    }

    record MyCommandPayload(String notRoutingKey) {

    }

    class ParentEntityWithWrongRoutingKeyChild {

        @EntityMember()
        private List<ChildEntityWithWrongRoutingKey> child;
    }

    class ChildEntityWithWrongRoutingKey {

        @RoutingKey
        private String id;
    }
}

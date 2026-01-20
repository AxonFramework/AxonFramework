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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.messaging.commandhandling.annotation.RoutingKey;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.entity.child.EventTargetMatcher;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingKeyEventTargetMatcherDefinitionTest {

    private final RoutingKeyEventTargetMatcherDefinition definition = new RoutingKeyEventTargetMatcherDefinition();


    @Test
    void allowsNoRoutingKeyOnSingleValueEntityMember() throws NoSuchFieldException {
        var childEntityMetamodel = mock(AnnotatedEntityMetamodel.class);
        when(childEntityMetamodel.entityType()).thenReturn(ChildEntityWithoutRoutingKey.class);
        EventTargetMatcher<ChildEntityWithoutRoutingKey> result = definition.createChildEntityMatcher(
                childEntityMetamodel,
                SimpleSingleChildValueEntity.class.getDeclaredField(
                        "child"));

        assertNotNull(result, "Expected a non-null EventTargetMatcher");
    }

    @Test
    void doesNotAllowMissingRoutingKeyOnCollectionTypeMember() {
        var childEntityMetamodel = mock(AnnotatedEntityMetamodel.class);
        when(childEntityMetamodel.entityType()).thenReturn(ChildEntityWithoutRoutingKey.class);
        assertThrows(AxonConfigurationException.class, () -> definition.createChildEntityMatcher(
                             childEntityMetamodel,
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
        AnnotatedEntityMetamodel<ChildEntityWithWrongRoutingKey> childEntityMetamodel = mock(
                AnnotatedEntityMetamodel.class);
        when(childEntityMetamodel.entityType()).thenReturn(ChildEntityWithWrongRoutingKey.class);

        EventTargetMatcher<ChildEntityWithWrongRoutingKey> resolver = definition.createChildEntityMatcher(
                childEntityMetamodel,
                ParentEntityWithWrongRoutingKeyChild.class.getDeclaredField("child"));

        MessageType messageType = new MessageType(MyCommandPayload.class);
        when(childEntityMetamodel.getExpectedRepresentation(messageType.qualifiedName())).thenReturn((Class) MyCommandPayload.class);

        assertThrows(UnknownRoutingKeyException.class, () -> {
            resolver.matches(
                    new ChildEntityWithWrongRoutingKey(),
                    new GenericEventMessage(messageType, new MyCommandPayload("someValue")),
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

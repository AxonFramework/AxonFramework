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

package org.axonframework.modelling.entity.annotations;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.annotations.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.annotations.TargetEntityId;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
class AnnotatedEntityIdResolverTest {

    @Mock
    private AnnotatedEntityMetamodel<String> metamodel;

    private AnnotatedEntityIdResolver<String> resolver;

    @BeforeEach
    void setUp() {
        resolver = new AnnotatedEntityIdResolver<>(metamodel,
                                                   String.class,
                                                   new DelegatingMessageConverter(new JacksonConverter()),
                                                   new AnnotationBasedEntityIdResolver<>());
    }

    @Test
    void canResolveIdFromSerializedMessage() {
        MessageType messageType = new MessageType(MyIdHoldingObject.class);
        var serializedMessage = new GenericMessage(messageType, """
                {"identifier": "test5362"}""");

        QualifiedName qualifiedName = messageType.qualifiedName();
        Mockito.doReturn(MyIdHoldingObject.class)
               .when(metamodel)
               .getExpectedRepresentation(qualifiedName);


        String resolvedId = resolver.resolve(serializedMessage, new StubProcessingContext());
        assertEquals("test5362", resolvedId);
    }

    @Test
    void throwsExceptionWhenExpectedRepresentationIsMissing() {
        MessageType messageType = new MessageType(MyIdHoldingObject.class);
        var serializedMessage = new GenericMessage(messageType, """
                {"identifier": "test5362"}""");

        assertThrows(AxonConfigurationException.class, () -> {
            resolver.resolve(serializedMessage, new StubProcessingContext());
        });
    }


    private record MyIdHoldingObject(
            @TargetEntityId String identifier
    ) {

    }
}
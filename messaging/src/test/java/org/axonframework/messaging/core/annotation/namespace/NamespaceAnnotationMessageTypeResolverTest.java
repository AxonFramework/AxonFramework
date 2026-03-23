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

package org.axonframework.messaging.core.annotation.namespace;

import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating that the {@link AnnotationMessageTypeResolver} resolves the
 * {@link org.axonframework.messaging.core.annotation.Namespace} annotation when present on a {@code package-info.java}
 * file.
 *
 * @author Steven van Beelen
 */
class NamespaceAnnotationMessageTypeResolverTest {

    private AnnotationMessageTypeResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationMessageTypeResolver();
    }

    @Test
    void namespaceAnnotationIsHonoredNamespaceAttribute() {
        Optional<MessageType> result = testSubject.resolve(MessageWithoutTypeNamespace.class);

        assertThat(result).isPresent();
        assertThat(result.get().qualifiedName().namespace()).isEqualTo("namespace-info");
    }

    @Event(name = "event")
    private record MessageWithoutTypeNamespace(String id) {

    }
}
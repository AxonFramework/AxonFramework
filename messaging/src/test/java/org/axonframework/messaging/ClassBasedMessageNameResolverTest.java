/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassBasedMessageNameResolverTest {

    @Test
    void shouldUseDefaultRevisionWhenCreatedWithDefaultConstructor() {
        // given
        ClassBasedMessageNameResolver resolver = new ClassBasedMessageNameResolver();

        // when
        QualifiedName resolvedName = resolver.resolve("Test");

        // then
        assertEquals(String.class.getPackageName(), resolvedName.namespace());
        assertEquals(String.class.getSimpleName(), resolvedName.localName());
        assertEquals(QualifiedNameUtils.DEFAULT_REVISION, resolvedName.revision());
    }

    @Test
    void shouldUseCustomRevisionWhenProvidedInConstructor() {
        // given
        String customRevision = "1.0.0";
        ClassBasedMessageNameResolver resolver = new ClassBasedMessageNameResolver(customRevision);

        // when
        QualifiedName resolvedName = resolver.resolve(42);

        // then
        assertEquals(Integer.class.getPackageName(), resolvedName.namespace());
        assertEquals(Integer.class.getSimpleName(), resolvedName.localName());
        assertEquals(customRevision, resolvedName.revision());
    }

    @Test
    void shouldResolvePrimitiveTypeToCorrespondingWrapperClass() {
        // given
        ClassBasedMessageNameResolver resolver = new ClassBasedMessageNameResolver();

        // when
        QualifiedName resolvedName = resolver.resolve(42);

        // then
        assertEquals(Integer.class.getPackageName(), resolvedName.namespace());
        assertEquals(Integer.class.getSimpleName(), resolvedName.localName());
        assertEquals(QualifiedNameUtils.DEFAULT_REVISION, resolvedName.revision());
    }

    @Test
    void shouldResolveCustomClassWithProvidedRevision() {
        // given
        String customRevision = "custom-rev";
        ClassBasedMessageNameResolver resolver = new ClassBasedMessageNameResolver(customRevision);

        // when
        class TestPayload {}
        QualifiedName resolvedName = resolver.resolve(new TestPayload());

        // then
        assertEquals(TestPayload.class.getPackageName(), resolvedName.namespace());
        assertEquals(TestPayload.class.getSimpleName(), resolvedName.localName());
        assertEquals(customRevision, resolvedName.revision());
    }
}
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

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link SimpleQualifiedName}.
 *
 * @author Steven van Beelen
 */
class SimpleQualifiedNameTest {

    @Test
    void containsDataAsExpected() {
        QualifiedName testSubject = new SimpleQualifiedName("namespace", "localName", "revision");

        assertEquals("namespace", testSubject.namespace());
        assertEquals("localName", testSubject.localName());
        assertEquals("revision", testSubject.revision());
    }

    @Test
    void defaultsNamespaceToEmptyStringWhenNullIsProvided() {
        QualifiedName testSubject = new SimpleQualifiedName(null, "localName", "revision");

        assertEquals("", testSubject.namespace());
    }

    @Test
    void throwsAxonConfigurationExceptionForNullLocalName() {
        assertThrows(AxonConfigurationException.class, () -> new SimpleQualifiedName("namespace", null, "revision"));
    }

    @Test
    void throwsAxonConfigurationExceptionForEmptyLocalName() {
        assertThrows(AxonConfigurationException.class, () -> new SimpleQualifiedName("namespace", "", "revision"));
    }

    @Test
    void defaultsRevisionToEmptyStringWhenNullIsProvided() {
        QualifiedName testSubject = new SimpleQualifiedName("namespace", "localName", null);

        assertEquals("", testSubject.revision());
    }

    @Test
    void toSimpleStringReturnsAsExpected() {
        QualifiedName testSubject = new SimpleQualifiedName("namespace", "localName", "revision");

        assertEquals("localName @(namespace) #[revision]", testSubject.toSimpleString());
    }
}
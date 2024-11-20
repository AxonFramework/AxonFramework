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
 * Test class validating the {@code static} builder methods and {@code default} methods of the {@link QualifiedName}.
 *
 * @author Steven van Beelen
 */
class QualifiedNameTest {

    @Test
    void classNameSplitsTheClassAsExpected() {
        String expectedNamespace = ClassToGiveNameTo.class.getPackageName();
        String expectedLocalName = ClassToGiveNameTo.class.getSimpleName();

        QualifiedName testSubject = QualifiedName.className(ClassToGiveNameTo.class);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
    }

    @Test
    void classNameThrowsAxonConfigurationExceptionForNullClass() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.className(null));
    }

    @Test
    void dottedNameSplitsTheNameAsExpected() {
        String expectedNamespace = "my.context";
        String expectedLocalName = "BusinessOperation";

        QualifiedName testSubject = QualifiedName.dottedName("my.context.BusinessOperation");

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
    }

    @Test
    void dottedNameThrowsAxonConfigurationExceptionForNullDottedName() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.dottedName(null));
    }

    @Test
    void dottedNameThrowsAxonConfigurationExceptionForEmptyDottedName() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.dottedName(""));
    }

    @Test
    void dottedNameThrowsAxonConfigurationExceptionForEmptyLocalNamePart() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.dottedName("my.context."));
    }

    @Test
    void simpleStringNameSplitsLocalNameOnlyQualifiedTypeAsExpected() {
        String expectedLocalName = "localName";

        QualifiedName testSubject = QualifiedName.simpleStringName(expectedLocalName);

        assertEquals("", testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals("", testSubject.revision());
    }

    @Test
    void simpleStringNameSplitsLocalNameAndNamespaceQualifiedTypeAsExpected() {
        String expectedNamespace = "namespace";
        String expectedLocalName = "localName";

        String testSimpleString = expectedLocalName + " @[" + expectedNamespace + "]";

        QualifiedName testSubject = QualifiedName.simpleStringName(testSimpleString);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals("", testSubject.revision());
    }

    @Test
    void simpleStringNameSplitsLocalNameAndRevisionQualifiedTypeAsExpected() {
        String expectedLocalName = "localName";
        String expectedRevision = "revision";

        String testSimpleString = expectedLocalName + " #[" + expectedRevision + "]";

        QualifiedName testSubject = QualifiedName.simpleStringName(testSimpleString);

        assertEquals("", testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(expectedRevision, testSubject.revision());
    }

    @Test
    void simpleStringNameSplitsFullQualifiedTypeAsExpected() {
        String expectedNamespace = "namespace";
        String expectedLocalName = "localName";
        String expectedRevision = "revision";

        String testSimpleString = expectedLocalName + " @[" + expectedNamespace + "] #[" + expectedRevision + "]";

        QualifiedName testSubject = QualifiedName.simpleStringName(testSimpleString);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(expectedRevision, testSubject.revision());
    }

    @Test
    void simpleStringNameThrowsAxonConfigurationExceptionForNullDottedName() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.simpleStringName(null));
    }

    @Test
    void simpleStringNameThrowsAxonConfigurationExceptionForEmptyDottedName() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.simpleStringName(""));
    }

    @Test
    void toSimpleStringReturnsLocalName() {
        QualifiedName testSubject = QualifiedName.dottedName("BusinessOperation");

        assertEquals("BusinessOperation", testSubject.toSimpleString());
    }

    @Test
    void toSimpleStringReturnsLocalNameAtNamespace() {
        QualifiedName testSubject = QualifiedName.dottedName("my.context.BusinessOperation");

        assertEquals("BusinessOperation @(my.context)", testSubject.toSimpleString());
    }

    private static class ClassToGiveNameTo {

    }
}
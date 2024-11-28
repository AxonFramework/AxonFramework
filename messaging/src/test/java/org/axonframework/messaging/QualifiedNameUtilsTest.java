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
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link QualifiedNameUtils}.
 *
 * @author Steven van Beelen
 */
class QualifiedNameUtilsTest {

    @Test
    void fromClassNameThrowsAxonConfigurationExceptionForNullClass() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedNameUtils.fromClassName(null));
    }

    @Test
    void fromClassNameSplitsTheClassAsExpected() {
        String expectedNamespace = ClassToGiveNameTo.class.getPackageName();
        String expectedLocalName = ClassToGiveNameTo.class.getSimpleName();

        QualifiedName testSubject = QualifiedNameUtils.fromClassName(ClassToGiveNameTo.class);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
    }

    @Test
    void fromDottedNameThrowsAxonConfigurationExceptionForNullFromDottedName() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedNameUtils.fromDottedName(null));
    }

    @Test
    void fromDottedNameThrowsAxonConfigurationExceptionForEmptyFromDottedName() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedNameUtils.fromDottedName(""));
    }

    @Test
    void fromDottedNameThrowsAxonConfigurationExceptionForEmptyLocalNamePart() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedNameUtils.fromDottedName("my.context."));
    }

    @Test
    void fromDottedNameSplitsTheNameAsExpected() {
        String expectedNamespace = "my.context";
        String expectedLocalName = "BusinessOperation";

        QualifiedName testSubject = QualifiedNameUtils.fromDottedName("my.context.BusinessOperation");

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(QualifiedNameUtils.DEFAULT_REVISION, testSubject.revision());
    }

    @Test
    void fromDottedNameWithRevisionSplitsTheNameAsExpected() {
        String expectedNamespace = "my.context";
        String expectedLocalName = "BusinessOperation";
        String testRevision = "1337.42";

        QualifiedName testSubject = QualifiedNameUtils.fromDottedName("my.context.BusinessOperation", testRevision);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(testRevision, testSubject.revision());
    }

    private static class ClassToGiveNameTo {

    }
}
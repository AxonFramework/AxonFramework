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
 * Test class validating the {@link QualifiedName}.
 *
 * @author Steven van Beelen
 */
class QualifiedNameTest {

    private static final String NAMESPACE = "namespace";
    private static final String LOCAL_NAME = "localName";
    private static final String REVISION = "revision";

    @Test
    void containsDataAsExpected() {
        QualifiedName testSubject = new QualifiedName(NAMESPACE, LOCAL_NAME, REVISION);

        assertEquals(NAMESPACE, testSubject.namespace());
        assertEquals(LOCAL_NAME, testSubject.localName());
        assertEquals(REVISION, testSubject.revision());
    }

    @Test
    void throwsAxonConfigurationExceptionForNullNamespace() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new QualifiedName(null, LOCAL_NAME, REVISION));
    }

    @Test
    void throwsAxonConfigurationExceptionForEmptyNamespace() {
        assertThrows(AxonConfigurationException.class, () -> new QualifiedName("", LOCAL_NAME, REVISION));
    }

    @Test
    void throwsAxonConfigurationExceptionForNullLocalName() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new QualifiedName(NAMESPACE, null, REVISION));
    }

    @Test
    void throwsAxonConfigurationExceptionForEmptyLocalName() {
        assertThrows(AxonConfigurationException.class, () -> new QualifiedName(NAMESPACE, "", REVISION));
    }

    @Test
    void throwsAxonConfigurationExceptionForNullRevision() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new QualifiedName(NAMESPACE, LOCAL_NAME, null));
    }

    @Test
    void throwsAxonConfigurationExceptionForEmptyRevision() {
        assertThrows(AxonConfigurationException.class, () -> new QualifiedName(NAMESPACE, LOCAL_NAME, ""));
    }

    @Test
    void toSimpleStringReturnsAsExpected() {
        QualifiedName testSubject = new QualifiedName(NAMESPACE, LOCAL_NAME, REVISION);

        assertEquals("localName @(namespace) #[revision]", testSubject.toSimpleString());
    }

    @Test
    void classNameFactoryMethodSplitsTheClassAsExpected() {
        String expectedNamespace = ClassToGiveNameTo.class.getPackageName();
        String expectedLocalName = ClassToGiveNameTo.class.getSimpleName();

        QualifiedName testSubject = QualifiedName.className(ClassToGiveNameTo.class);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
    }

    @Test
    void classNameFactoryMethodThrowsAxonConfigurationExceptionForNullClass() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.className(null));
    }

    @Test
    void dottedNameFactoryMethodThrowsAxonConfigurationExceptionForNullDottedName() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.dottedName(null));
    }

    @Test
    void dottedNameFactoryMethodThrowsAxonConfigurationExceptionForEmptyDottedName() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.dottedName(""));
    }

    @Test
    void dottedNameFactoryMethodThrowsAxonConfigurationExceptionForEmptyLocalNamePart() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.dottedName("my.context."));
    }

    @Test
    void dottedNameFactoryMethodSplitsTheNameAsExpected() {
        String expectedNamespace = "my.context";
        String expectedLocalName = "BusinessOperation";

        QualifiedName testSubject = QualifiedName.dottedName("my.context.BusinessOperation");

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(QualifiedName.DEFAULT_REVISION, testSubject.revision());
    }

    @Test
    void dottedNameFactoryMethodWithRevisionSplitsTheNameAsExpected() {
        String expectedNamespace = "my.context";
        String expectedLocalName = "BusinessOperation";
        String testRevision = "1337.42";

        QualifiedName testSubject = QualifiedName.dottedName("my.context.BusinessOperation", testRevision);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(testRevision, testSubject.revision());
    }

    @Test
    void simpleStringNameFactoryMethodSplitsLocalNameOnlyQualifiedTypeAsExpected() {
        String expectedLocalName = LOCAL_NAME;

        QualifiedName testSubject = QualifiedName.simpleStringName(expectedLocalName);

        assertEquals("", testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
    }

    @Test
    void simpleStringNameFactoryMethodSplitsLocalNameAndNamespaceQualifiedTypeAsExpected() {
        String expectedNamespace = NAMESPACE;
        String expectedLocalName = LOCAL_NAME;

        String testSimpleString = expectedLocalName + " @[" + expectedNamespace + "]";

        QualifiedName testSubject = QualifiedName.simpleStringName(testSimpleString);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
    }

    @Test
    void simpleStringNameFactoryMethodSplitsLocalNameAndRevisionQualifiedTypeAsExpected() {
        String expectedLocalName = LOCAL_NAME;
        String expectedRevision = REVISION;

        String testSimpleString = expectedLocalName + " #[" + expectedRevision + "]";

        QualifiedName testSubject = QualifiedName.simpleStringName(testSimpleString);

        assertEquals("", testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(expectedRevision, testSubject.revision());
    }

    @Test
    void simpleStringNameFactoryMethodSplitsFullQualifiedTypeAsExpected() {
        String expectedNamespace = NAMESPACE;
        String expectedLocalName = LOCAL_NAME;
        String expectedRevision = REVISION;

        String testSimpleString = expectedLocalName + " @[" + expectedNamespace + "] #[" + expectedRevision + "]";

        QualifiedName testSubject = QualifiedName.simpleStringName(testSimpleString);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(expectedRevision, testSubject.revision());
    }

    @Test
    void simpleStringNameFactoryMethodThrowsAxonConfigurationExceptionForNullSimpleString() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.simpleStringName(null));
    }

    @Test
    void simpleStringNameFactoryMethodThrowsAxonConfigurationExceptionForEmptySimpleString() {
        assertThrows(AxonConfigurationException.class, () -> QualifiedName.simpleStringName(""));
    }

    @Test
    void simpleStringNameFactoryMethodThrowsAxonConfigurationExceptionForMalformedString() {
        assertThrows(AxonConfigurationException.class,
                     () -> QualifiedName.simpleStringName("this doesn't match"));
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
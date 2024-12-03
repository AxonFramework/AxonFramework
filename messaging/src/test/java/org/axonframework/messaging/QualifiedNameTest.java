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

import org.junit.jupiter.api.*;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link QualifiedName}.
 *
 * @author Steven van Beelen
 */
class QualifiedNameTest {

    private static final String NAMESPACE = "namespace";
    private static final String LOCAL_NAME = "localName";
    private static final String REVISION = "0.0.1";

    @Test
    void containsDataAsExpected() {
        QualifiedName testSubject = new QualifiedName(NAMESPACE, LOCAL_NAME, REVISION);

        assertEquals(NAMESPACE, testSubject.namespace());
        assertEquals(LOCAL_NAME, testSubject.localName());
        assertEquals(REVISION, testSubject.revision());
    }

    @Test
    void throwsIllegalArgumentExceptionForNullNamespace() {
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(null, LOCAL_NAME, REVISION));
    }

    @Test
    void throwsIllegalArgumentExceptionForEmptyNamespace() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName("", LOCAL_NAME, REVISION));
    }

    @Test
    void throwsIllegalArgumentExceptionForNamespaceContainingSemicolons() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName("name:space", LOCAL_NAME, REVISION));
    }

    @Test
    void throwsIllegalArgumentExceptionForNullLocalName() {
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(NAMESPACE, null, REVISION));
    }

    @Test
    void throwsIllegalArgumentExceptionForEmptyLocalName() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(NAMESPACE, "", REVISION));
    }

    @Test
    void throwsIllegalArgumentExceptionForLocalNameContainingSemicolons() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(NAMESPACE, "local:name", REVISION));
    }

    @Test
    void throwsIllegalArgumentExceptionForNullRevision() {
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(NAMESPACE, LOCAL_NAME, null));
    }

    @Test
    void throwsIllegalArgumentExceptionForEmptyRevision() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(NAMESPACE, LOCAL_NAME, ""));
    }

    @Test
    void throwsIllegalArgumentExceptionForRevisionContainingSemicolons() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName(NAMESPACE, LOCAL_NAME, "blue:green"));
    }

    @Test
    void toStringReturnsAsExpected() {
        QualifiedName testSubject = new QualifiedName(NAMESPACE, LOCAL_NAME, REVISION);

        assertEquals("namespace:localName:0.0.1", testSubject.toString());
    }

    @Test
    void fromStringThrowsAxonConfigurationExceptionForNullFromString() {
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> QualifiedName.fromString(null));
    }

    @Test
    void fromStringThrowsAxonConfigurationExceptionForEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> QualifiedName.fromString(""));
    }

    /**
     * This test case validates the format is exactly {@code namespace:localName:revision}, with no additional
     * semicolons spread within.
     */
    @Test
    void fromStringThrowsAxonConfigurationExceptionWhenDelimiterCountIsNotExactlyTwo() {
        String baseText = "Lorem";
        int[] scenarios = new int[]{1, 3, 4, 5, 6, 7, 8, 9, 10};
        for (int scenario : scenarios) {
            String testText = IntStream.range(0, scenario)
                                       .mapToObj(delimiterCount -> baseText)
                                       .reduce(baseText, (result, base) -> result + QualifiedName.DELIMITER + base);
            assertThrows(IllegalArgumentException.class,
                         () -> QualifiedName.fromString(testText),
                         () -> "Failed in the scenario with #" + scenario + " delimiters: " + testText);
        }
    }

    @Test
    void fromStringSplitsFullQualifiedTypeAsExpected() {
        String expectedNamespace = NAMESPACE;
        String expectedLocalName = LOCAL_NAME;
        String expectedRevision = REVISION;

        String testSimpleString = expectedNamespace + QualifiedName.DELIMITER
                + expectedLocalName + QualifiedName.DELIMITER + expectedRevision;

        QualifiedName testSubject = QualifiedName.fromString(testSimpleString);

        assertEquals(expectedNamespace, testSubject.namespace());
        assertEquals(expectedLocalName, testSubject.localName());
        assertEquals(expectedRevision, testSubject.revision());
    }
}
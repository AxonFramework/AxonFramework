/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Allard Buijze
 */
class AnnotationRevisionResolverTest {

    private AnnotationRevisionResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationRevisionResolver();
    }

    @Test
    void revisionOfAnnotatedClass() {
        assertEquals("2.3-TEST", testSubject.revisionOf(WithAnnotation.class));
    }

    @Test
    void revisionOfNonAnnotatedClass() {
        assertNull(testSubject.revisionOf(WithoutAnnotation.class));
    }

    @Revision("2.3-TEST")
    private class WithAnnotation {

    }

    private class WithoutAnnotation {

    }
}

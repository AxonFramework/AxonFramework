/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class AnnotationRevisionResolverTest {

    private AnnotationRevisionResolver testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AnnotationRevisionResolver();
    }

    @Test
    public void testRevisionOfAnnotatedClass() throws Exception {
        assertEquals("2.3-TEST", testSubject.revisionOf(WithAnnotation.class));
    }

    @Test
    public void testRevisionOfNonAnnotatedClass() throws Exception {
        assertEquals(null, testSubject.revisionOf(WithoutAnnotation.class));
    }

    @Revision("2.3-TEST")
    private class WithAnnotation {

    }

    private class WithoutAnnotation {

    }
}

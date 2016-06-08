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

import java.io.Serializable;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class SerialVersionUIDRevisionResolverTest {

    private SerialVersionUIDRevisionResolver testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new SerialVersionUIDRevisionResolver();
    }

    @Test
    public void testRevisionOfAnnotatedClass() throws Exception {
        assertEquals("7038084420164786502", testSubject.revisionOf(IsSerializable.class));
    }

    @Test
    public void testRevisionOfNonAnnotatedClass() throws Exception {
        assertEquals(null, testSubject.revisionOf(NotSerializable.class));
    }

    private static class IsSerializable implements Serializable {
        private static final long serialVersionUID = 7038084420164786502L;
    }

    private static class NotSerializable {

    }
}

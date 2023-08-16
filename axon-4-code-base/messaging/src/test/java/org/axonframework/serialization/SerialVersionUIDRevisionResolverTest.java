/*
 * Copyright (c) 2010-2023. Axon Framework
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

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Allard Buijze
 */
class SerialVersionUIDRevisionResolverTest {

    private SerialVersionUIDRevisionResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new SerialVersionUIDRevisionResolver();
    }

    @Test
    void revisionOfAnnotatedClass() {
        assertEquals("7038084420164786502", testSubject.revisionOf(IsSerializable.class));
    }

    @Test
    void revisionOfNonAnnotatedClass() {
        assertNull(testSubject.revisionOf(NotSerializable.class));
    }

    private static class IsSerializable implements Serializable {
        private static final long serialVersionUID = 7038084420164786502L;
    }

    private static class NotSerializable {

    }
}

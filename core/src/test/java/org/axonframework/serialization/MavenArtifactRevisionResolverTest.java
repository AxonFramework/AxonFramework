/*
 * Copyright (c) 2010-2013. Axon Framework
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Allard Buijze
 */
public class MavenArtifactRevisionResolverTest {

    @Test
    public void testFindVersionOfExistingPomProperties() throws Exception {
        MavenArtifactRevisionResolver testSubject = new MavenArtifactRevisionResolver("org.axonframework", "axon-core");

        assertEquals("2.1-SNAPSHOT", testSubject.revisionOf(Object.class));
    }

    @Test
    public void testFindVersionOfNonExistingProperties() throws Exception {
        MavenArtifactRevisionResolver testSubject = new MavenArtifactRevisionResolver("does.not.exist", "axon-core");

        assertNull(testSubject.revisionOf(Object.class));
    }
}

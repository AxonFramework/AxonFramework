/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.domain;

import org.junit.*;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class UUIDAggregateIdentifierTest {

    @Test
    public void testGenerationRandomIdentifier() {
        UUIDAggregateIdentifier id1 = new UUIDAggregateIdentifier();
        UUIDAggregateIdentifier id2 = new UUIDAggregateIdentifier();

        assertThat(id1, not(equalTo(id2)));
    }

    @Test
    public void testGenerateEqualIdentifiers() {
        UUID uuid = UUID.randomUUID();
        UUIDAggregateIdentifier id1 = new UUIDAggregateIdentifier(uuid);
        UUIDAggregateIdentifier id2 = new UUIDAggregateIdentifier(uuid);

        assertThat(id1, equalTo(id2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateNonUUIDIdentifiers() {
        new UUIDAggregateIdentifier("not a uuid");
    }

    @Test
    public void testAsUUID() {
        UUID uuid = UUID.randomUUID();
        UUIDAggregateIdentifier id1 = new UUIDAggregateIdentifier(uuid);

        assertThat(id1.asUUID(), equalTo(uuid));
    }
}

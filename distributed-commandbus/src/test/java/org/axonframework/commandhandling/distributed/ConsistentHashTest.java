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

package org.axonframework.commandhandling.distributed;

import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class ConsistentHashTest {

    /*
    Hash Ring:
        0116abfa0f31cc829b7c3a9c8d2d1705 -> Node1 (String)
        1c0eee159e4e8220d819902d979f6730 -> Node3 (int)
        72d267ad5b5442df8a0dd82e79096679 -> Node3 (int)
        807e2793b9a42e9be707b54ff07a09b7 -> Node2 (String)
        8b099041491a7a7a1a56fa02e8ab21c5 -> Node3 (String)
        be5370313c2620892c6a84251e0b1ed2 -> Node3 (int)
        c4d7768e4a7a52aa1e5fbee1679590e7 -> Node1 (String)
        ca310180dbab16934e5a40f3909150f8 -> Node2 (String)
        ee4efcc2ea892a2ce19300a1522d69d1 -> Node2 (String)

    Hashes of used values:
    value1 -> 9946687e5fa0dab5993ededddb398d2e
    value2 -> f066ce9385512ee02afc6e14d627e9f2
    value3 -> 039da699d091de4f1240ae50570abed9
    value4 -> b600fc6b6ea1955d114861c42934c659
    int-value4 -> c76e70fe72b2b988cc2d69c7b7d3f4fb
    */

    private ConsistentHash testSubject;

    @Before
    public void setUp() {
        testSubject = ConsistentHash.emptyRing();
        testSubject = testSubject.withAdditionalNode("Node1", 2, singleton("String"));
        testSubject = testSubject.withAdditionalNode("Node2", 3, singleton("String"));
        testSubject = testSubject.withAdditionalNode("Node3", 4, singleton("int"));
    }

    @Test
    public void testToString() {
        String actual = ConsistentHash.emptyRing().toString();
        assertEquals("ConsistentHash: {}", actual);

        actual = ConsistentHash.emptyRing().withAdditionalNode("Node", 2, singleton("String")).toString();
        assertEquals(
                "ConsistentHash: {\n2d9aa5c8a1cef20eafd756e8c58f1b29 -> Node(String), \ndc17bfc640c5e43ec7ecdfa874e703b2 -> Node(String)\n}",
                actual);
    }

    @Test
    public void testGetMembers() throws Exception {
        ConsistentHash ring = ConsistentHash.emptyRing()
                                            .withAdditionalNode("Node1", 2, singleton("String"))
                                            .withAdditionalNode("Node2", 4, singleton("String"));

        Set<ConsistentHash.Member> actual = ring.getMembers();
        assertEquals(2, actual.size());
        assertEquals(singleton("String"), actual.iterator().next().supportedCommands());
        final Iterator<ConsistentHash.Member> members = actual.iterator();
        Set<Integer> values = new HashSet<>(Arrays.asList(2, 4));
        while (!values.isEmpty()) {
            assertTrue(values.remove(members.next().segmentCount()));
        }
    }

    @Test
    public void testGetNodeName() {
        assertEquals("Node3", testSubject.getMember("Node3 #0", "int"));
        assertEquals("Node1", testSubject.getMember("Node3 #0", "String"));
        assertEquals("Node3", testSubject.getMember("value1", "int"));
        assertEquals("Node1", testSubject.getMember("value1", "String"));
        assertEquals("Node1", testSubject.getMember("value2", "String"));
        assertEquals("Node3", testSubject.getMember("value2", "int"));
        assertEquals("Node3", testSubject.getMember("value3", "int"));
        assertEquals("Node3", testSubject.getMember("value4", "int"));
        assertEquals("Node1", testSubject.getMember("value4", "String"));
        assertEquals("Node3", testSubject.getMember("int-value4", "int"));
    }

    @Test
    public void testRemoveNodes() {
        testSubject = testSubject.withExclusively(Arrays.asList("Node1", "Node2"));
        assertEquals("Node1", testSubject.getMember("value1", "String"));
        assertEquals("Node1", testSubject.getMember("value2", "String"));
        assertEquals("Node2", testSubject.getMember("value3", "String"));
        assertEquals("Node1", testSubject.getMember("value4", "String"));
    }

    @Test
    public void testAdditionalNodeRemovedOldReferences() {
        testSubject = ConsistentHash.emptyRing()
                                    .withAdditionalNode("Node", 15, singleton("int"))
                                    .withAdditionalNode("Node", 10, singleton("String"));
        ConsistentHash reference = ConsistentHash.emptyRing().withAdditionalNode("Node", 10, singleton("String"));

        assertEquals(reference, testSubject);
    }

    @Test
    public void testExternalization() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(baos);
        out.writeObject(testSubject);
        out.close();
        Object actual = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject();
        assertEquals(testSubject, actual);
    }

    @Test
    public void testEqualsAndHashCode() {
        assertTrue(testSubject.equals(testSubject));
        assertFalse(testSubject.equals(new Object()));

        assertEquals(testSubject.hashCode(), testSubject.hashCode());

        ConsistentHash copy = ConsistentHash.emptyRing()
                                            .withAdditionalNode("Node1", 2, singleton("String"))
                                            .withAdditionalNode("Node2", 3, singleton("String"))
                                            .withAdditionalNode("Node3", 4, singleton("int"));

        assertTrue(testSubject.equals(copy));
        assertEquals(testSubject.hashCode(), copy.hashCode());
        assertNotSame(testSubject.hashCode(), copy.withExclusively(Arrays.asList("Node1")).hashCode());
    }
}

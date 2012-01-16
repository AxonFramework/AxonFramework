package org.axonframework.commandbus.distributed;

import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class ConsistentHashTest {

    /*
    Hash Ring:
        0116abfa0f31cc829b7c3a9c8d2d1705 -> Node1
        1c0eee159e4e8220d819902d979f6730 -> Node3
        72d267ad5b5442df8a0dd82e79096679 -> Node3
        807e2793b9a42e9be707b54ff07a09b7 -> Node2
        8b099041491a7a7a1a56fa02e8ab21c5 -> Node3
        be5370313c2620892c6a84251e0b1ed2 -> Node3
        c4d7768e4a7a52aa1e5fbee1679590e7 -> Node1
        ca310180dbab16934e5a40f3909150f8 -> Node2
        ee4efcc2ea892a2ce19300a1522d69d1 -> Node2

    Hashes of used values:
    value1 -> 9946687e5fa0dab5993ededddb398d2e
    value2 -> f066ce9385512ee02afc6e14d627e9f2
    value3 -> 039da699d091de4f1240ae50570abed9
    value4 -> b600fc6b6ea1955d114861c42934c659
    */

    private ConsistentHash testSubject;

    @Before
    public void setUp() {
        testSubject = ConsistentHash.emptyRing();
        testSubject = testSubject.withAdditionalNode("Node1", 2);
        testSubject = testSubject.withAdditionalNode("Node2", 3);
        testSubject = testSubject.withAdditionalNode("Node3", 4);
    }

    @Test
    public void testToString() {
        String actual = ConsistentHash.emptyRing().toString();
        assertEquals("ConsistentHash: {}", actual);

        actual = ConsistentHash.emptyRing().withAdditionalNode("Node", 2).toString();
        assertEquals(
                "ConsistentHash: {2d9aa5c8a1cef20eafd756e8c58f1b29 -> Node, dc17bfc640c5e43ec7ecdfa874e703b2 -> Node}",
                actual);
    }

    @Test
    public void testGetNodeName() {
        assertEquals("Node3", testSubject.getNodeName("Node3 #0"));
        assertEquals("Node3", testSubject.getNodeName("value1"));
        assertEquals("Node1", testSubject.getNodeName("value2"));
        assertEquals("Node3", testSubject.getNodeName("value3"));
        assertEquals("Node3", testSubject.getNodeName("value4"));
    }

    @Test
    public void testRemoveNodes() {
        testSubject = testSubject.withExclusively(Arrays.asList("Node1", "Node2"));
        assertEquals("Node1", testSubject.getNodeName("value1"));
        assertEquals("Node1", testSubject.getNodeName("value2"));
        assertEquals("Node2", testSubject.getNodeName("value3"));
        assertEquals("Node1", testSubject.getNodeName("value4"));
    }

    @Test
    public void testAdditionalNodeRemovedOldReferences() {
        testSubject = ConsistentHash.emptyRing()
                                    .withAdditionalNode("Node", 15)
                                    .withAdditionalNode("Node", 10);
        ConsistentHash reference = ConsistentHash.emptyRing().withAdditionalNode("Node", 10);

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
                                            .withAdditionalNode("Node1", 2)
                                            .withAdditionalNode("Node2", 3)
                                            .withAdditionalNode("Node3", 4);

        assertTrue(testSubject.equals(copy));
        assertEquals(testSubject.hashCode(), copy.hashCode());
        assertNotSame(testSubject.hashCode(), copy.withExclusively(Arrays.asList("Node1")).hashCode());
    }
}

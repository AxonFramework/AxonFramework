package org.axonframework.serialization.compression;

import org.axonframework.eventsourcing.StubDomainEvent;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class GzipSerializerWrapperWithXStreamTest {

    private GzipSerializerWrapper testSubject;
    private XStreamSerializer embeddedSerializer;
    private static final String SPECIAL__CHAR__STRING = "Special chars: '\"&;\n\\<>/\n\t";
    private static final String REGULAR_STRING = "Henk";
    private TestEvent testEvent;

    @Before
    public void setUp() {
        this.embeddedSerializer = new XStreamSerializer();
        this.testSubject = new GzipSerializerWrapper(embeddedSerializer);
        this.testEvent = new TestEvent(REGULAR_STRING);
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent() {
        SerializedObject<byte[]> serializedEvent = testSubject.serialize(testEvent, byte[].class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertTrue(actualResult instanceof TestEvent);
        TestEvent actualEvent = (TestEvent) actualResult;
        assertEquals(testEvent, actualEvent);
    }

    @Test
    public void testDeserializeUncompressedTestEvent() {
        SerializedObject<byte[]> serializedEvent = embeddedSerializer.serialize(testEvent, byte[].class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertTrue(actualResult instanceof TestEvent);
        TestEvent actualEvent = (TestEvent) actualResult;
        assertEquals(testEvent, actualEvent);
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent_WithXomUpcasters(){
        SerializedObject<nu.xom.Document> serializedEvent = testSubject.serialize(testEvent, nu.xom.Document.class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertEquals(testEvent, actualResult);
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent_WithDom4JUpcasters() {
        SerializedObject<org.dom4j.Document> serializedEvent = testSubject.serialize(testEvent,
                                                                                     org.dom4j.Document.class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertEquals(testEvent, actualResult);
    }

    @Test
    public void testPackageAlias() throws UnsupportedEncodingException {
        embeddedSerializer.addPackageAlias("axones", "org.axonframework.eventsourcing");
        embeddedSerializer.addPackageAlias("axon", "org.axonframework");

        SerializedObject<byte[]> serialized = testSubject.serialize(new StubDomainEvent(), byte[].class);
        String asString = new String(decompress(serialized.getData()), "UTF-8");
        assertFalse("Package name found in:" +  asString, asString.contains("org.axonframework.domain"));
        StubDomainEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(StubDomainEvent.class, deserialized.getClass());
        assertTrue(asString.contains("axones"));
    }

    @Test
    public void testAlias() throws UnsupportedEncodingException {
        embeddedSerializer.addAlias("stub", StubDomainEvent.class);

        SerializedObject<byte[]> serialized = testSubject.serialize(new StubDomainEvent(), byte[].class);
        String asString = new String(decompress(serialized.getData()), "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("<stub"));
        StubDomainEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(StubDomainEvent.class, deserialized.getClass());
    }

    @Test
    public void testFieldAlias() throws UnsupportedEncodingException {
        embeddedSerializer.addFieldAlias("relevantPeriod", TestEvent.class, "period");

        SerializedObject<byte[]> serialized = testSubject.serialize(testEvent, byte[].class);
        String asString = new String(decompress(serialized.getData()), "UTF-8");
        assertFalse(asString.contains("period"));
        assertTrue(asString.contains("<relevantPeriod"));
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertNotNull(deserialized);
    }

    @Test
    public void testRevisionNumber() throws UnsupportedEncodingException {
        SerializedObject<byte[]> serialized = testSubject.serialize(new RevisionSpecifiedEvent(), byte[].class);
        assertNotNull(serialized);
        assertEquals("2", serialized.getType().getRevision());
        assertEquals(RevisionSpecifiedEvent.class.getName(), serialized.getType().getName());
    }

    @Test
    public void testSerializedTypeUsesClassAlias() throws UnsupportedEncodingException {
        embeddedSerializer.addAlias("rse", RevisionSpecifiedEvent.class);
        SerializedObject<byte[]> serialized = testSubject.serialize(new RevisionSpecifiedEvent(), byte[].class);
        assertNotNull(serialized);
        assertEquals("2", serialized.getType().getRevision());
        assertEquals("rse", serialized.getType().getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithDom4JUpcasters() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING), byte[].class);
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertArrayEquals(SPECIAL__CHAR__STRING.getBytes(), deserialized.getName().getBytes());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithoutUpcasters() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING), byte[].class);
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(SPECIAL__CHAR__STRING, deserialized.getName());
    }

    private byte[] decompress(byte[] compressedData) {
        byte[] result = new byte[]{};
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
             ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPInputStream gzipIS = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIS.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            result = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Revision("2")
    public static class RevisionSpecifiedEvent {
    }

    public static class TestEvent implements Serializable {

        private static final long serialVersionUID = 1L;
        private String name;
        private LocalDate date;
        private Instant dateTime;
        private Period period;

        public TestEvent(String name) {
            this.name = name;
            this.date = LocalDate.now();
            this.dateTime = Instant.now();
            this.period = Period.ofDays(100);
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TestEvent testEvent = (TestEvent) o;

            if (date != null ? !date.equals(testEvent.date) : testEvent.date != null) {
                return false;
            }
            if (dateTime != null ? !dateTime.equals(testEvent.dateTime) : testEvent.dateTime != null) {
                return false;
            }
            if (name != null ? !name.equals(testEvent.name) : testEvent.name != null) {
                return false;
            }
            if (period != null ? !period.equals(testEvent.period) : testEvent.period != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (date != null ? date.hashCode() : 0);
            result = 31 * result + (dateTime != null ? dateTime.hashCode() : 0);
            result = 31 * result + (period != null ? period.hashCode() : 0);
            return result;
        }

    }
}



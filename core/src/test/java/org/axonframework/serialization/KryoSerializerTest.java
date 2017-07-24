package org.axonframework.serialization;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.SortedSet;

import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.kryo.KryoSerializer;
import org.junit.Before;
import org.junit.Test;

/**
 * Created on 31/01/17.
 *
 * @author Reda.Housni-Alaoui
 */
public class KryoSerializerTest {

	private KryoSerializer testSubject;

	@Before
	public void setUp() throws Exception {
		testSubject = new KryoSerializer();
	}

	@Test
	public void testSerializeAndDeserialize() {
		SerializedObject<byte[]> serializedObject = testSubject.serialize(new SimpleObject("hello"),
				byte[].class);
		assertEquals(SimpleObject.class.getName(), serializedObject.getType().getName());
		assertEquals("2166108932776672373", serializedObject.getType().getRevision());

		Object actualResult = testSubject.deserialize(serializedObject);
		assertTrue(actualResult instanceof SimpleObject);
		assertEquals("hello", ((SimpleObject) actualResult).getSomeProperty());
	}

	@Test
	public void testClassForType() {
		Class actual = testSubject.classForType(new SimpleSerializedType(SimpleObject.class.getName(),
				"2166108932776672373"));
		assertEquals(SimpleObject.class, actual);
	}

	@Test
	public void testClassForType_CustomRevisionResolver() {
		testSubject = new KryoSerializer(new FixedValueRevisionResolver("fixed"));
		Class actual = testSubject.classForType(new SimpleSerializedType(SimpleObject.class.getName(),
				"fixed"));
		assertEquals(SimpleObject.class, actual);
	}

	@Test
	public void testClassForType_UnknownClass() {
		try {
			testSubject.classForType(new SimpleSerializedType("unknown", "0"));
			fail("Expected UnknownSerializedTypeException");
		} catch (UnknownSerializedTypeException e) {
			assertTrue("Wrong message in exception", e.getMessage().contains("unknown"));
			assertTrue("Wrong message in exception", e.getMessage().contains("0"));
		}
	}

	@Test
	public void testMetaDataClass() {
		MetaData metaData = MetaData.with("hello", "world");
		SerializedObject<byte[]> serialized = testSubject.serialize(metaData, byte[].class);
		MetaData deserialized = testSubject.deserialize(serialized);

		assertEquals("world", deserialized.get("hello"));
	}

	@Test
	public void testSortedSet() {
		SortedSet<Object> sortedSet = Collections.emptySortedSet();
		SerializedObject<byte[]> serialized = testSubject.serialize(sortedSet, byte[].class);
		Object deserialized = testSubject.deserialize(serialized);
		assertTrue(deserialized instanceof SortedSet);
	}

	@Revision("2166108932776672373")
	private static class SimpleObject {

		private String someProperty;

		public SimpleObject(String someProperty) {
			this.someProperty = someProperty;
		}

		public String getSomeProperty() {
			return someProperty;
		}
	}
}

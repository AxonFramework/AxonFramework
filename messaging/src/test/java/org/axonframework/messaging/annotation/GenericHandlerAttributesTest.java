package org.axonframework.messaging.annotation;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericHandlerAttributes}.
 *
 * @author Steven van Beelen
 */
class GenericHandlerAttributesTest {

    private static final String ATTRIBUTE_KEY = "some-handler.some-attribute";
    private static final int ATTRIBUTE = 42;

    @Test
    void testConstructEmptyHandlerAttributes() {
        GenericHandlerAttributes testSubject = new GenericHandlerAttributes();

        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testConstructNonEmptyHandlerAttributes() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        GenericHandlerAttributes testSubject = new GenericHandlerAttributes(testAttributes);

        assertFalse(testSubject.isEmpty());
        assertTrue(testSubject.contains(ATTRIBUTE_KEY));
        assertEquals(testAttributes, testSubject.getAll());
    }

    @Test
    void testGet() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        GenericHandlerAttributes testSubject = new GenericHandlerAttributes(testAttributes);

        assertEquals(ATTRIBUTE, (int) testSubject.get(ATTRIBUTE_KEY));
    }

    @Test
    void testGetAll() {
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        GenericHandlerAttributes testSubject = new GenericHandlerAttributes(expectedAttributes);

        assertEquals(expectedAttributes, testSubject.getAll());
    }

    @Test
    void testContains() {
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        GenericHandlerAttributes testSubject = new GenericHandlerAttributes(expectedAttributes);

        assertTrue(testSubject.contains(ATTRIBUTE_KEY));
        assertFalse(testSubject.contains("some-other-handler"));
    }

    @Test
    void testWithAttributes() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        Map<String, Object> testWithAttributes = new HashMap<>();
        testWithAttributes.put("some-other-key", 1729);

        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        expectedAttributes.put("some-other-key", 1729);

        GenericHandlerAttributes testSubject = new GenericHandlerAttributes(testAttributes);

        GenericHandlerAttributes result = testSubject.mergedWith(testWithAttributes);

        assertEquals(expectedAttributes, result.getAll());
    }
}
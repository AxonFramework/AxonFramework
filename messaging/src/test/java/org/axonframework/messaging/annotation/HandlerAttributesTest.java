package org.axonframework.messaging.annotation;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link HandlerAttributes}.
 *
 * @author Steven van Beelen
 */
class HandlerAttributesTest {

    private static final String HANDLER_TYPE = "some-handler";
    private static final String ATTRIBUTE_NAME = "some-attribute";
    private static final int ATTRIBUTE = 42;

    @Test
    void testConstructEmptyHandlerAttributes() {
        HandlerAttributes testSubject = new HandlerAttributes();

        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testConstructNonEmptyHandlerAttributes() {
        Map<String, Map<String, Object>> handlerAttributesMap = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);
        handlerAttributesMap.put(HANDLER_TYPE, attributes);

        HandlerAttributes testSubject = new HandlerAttributes(handlerAttributesMap);

        assertFalse(testSubject.isEmpty());
        assertTrue(testSubject.containsAttributesFor(HANDLER_TYPE));
        assertEquals(attributes, testSubject.get(HANDLER_TYPE));
    }

    @Test
    void testPut() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);

        HandlerAttributes testSubject = new HandlerAttributes();
        testSubject.put(HANDLER_TYPE, attributes);

        assertTrue(testSubject.containsAttributesFor(HANDLER_TYPE));
        assertEquals(attributes, testSubject.get(HANDLER_TYPE));
    }

    @Test
    void testGet() {
        Map<String, Map<String, Object>> handlerAttributesMap = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);
        handlerAttributesMap.put(HANDLER_TYPE, attributes);

        HandlerAttributes testSubject = new HandlerAttributes(handlerAttributesMap);
        Map<String, Object> result = testSubject.get(HANDLER_TYPE);

        assertEquals(attributes, result);
    }

    @Test
    void testGetAll() {
        Map<String, Map<String, Object>> expected = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);
        expected.put(HANDLER_TYPE, attributes);

        HandlerAttributes testSubject = new HandlerAttributes(expected);
        Map<String, Map<String, Object>> result = testSubject.getAll();

        assertEquals(expected, result);
    }

    @Test
    void testGetAllPrefixed() {
        Map<String, Object> expected = new HashMap<>();
        expected.put(HANDLER_TYPE + "." + ATTRIBUTE_NAME, ATTRIBUTE);

        Map<String, Map<String, Object>> handlerAttributesMap = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);
        handlerAttributesMap.put(HANDLER_TYPE, attributes);
        HandlerAttributes testSubject = new HandlerAttributes(handlerAttributesMap);

        Map<String, Object> result = testSubject.getAllPrefixed();

        assertEquals(expected, result);
    }

    @Test
    void testContainsAttributesFor() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);

        HandlerAttributes testSubject = new HandlerAttributes();
        testSubject.put(HANDLER_TYPE, attributes);

        assertTrue(testSubject.containsAttributesFor(HANDLER_TYPE));
        assertFalse(testSubject.containsAttributesFor("some-other-handler"));
    }

    @Test
    void testIsEmpty() {
        Map<String, Map<String, Object>> handlerAttributesMap = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_NAME, ATTRIBUTE);
        handlerAttributesMap.put(HANDLER_TYPE, attributes);

        assertTrue(new HandlerAttributes().isEmpty());
        assertFalse(new HandlerAttributes(handlerAttributesMap).isEmpty());
    }
}
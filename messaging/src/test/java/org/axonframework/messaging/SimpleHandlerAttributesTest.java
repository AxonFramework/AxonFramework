package org.axonframework.messaging;

import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SimpleHandlerAttributes}.
 *
 * @author Steven van Beelen
 */
class SimpleHandlerAttributesTest {

    private static final String ATTRIBUTE_KEY = "some-handler.some-attribute";
    private static final int ATTRIBUTE = 42;

    @Test
    void constructEmptyHandlerAttributes() {
        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(Collections.emptyMap());

        assertTrue(testSubject.isEmpty());
    }

    @Test
    void constructNonEmptyHandlerAttributes() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        assertFalse(testSubject.isEmpty());
        assertTrue(testSubject.contains(ATTRIBUTE_KEY));
        assertEquals(testAttributes, testSubject.getAll());
    }

    @Test
    void get() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        assertEquals(ATTRIBUTE, (int) testSubject.get(ATTRIBUTE_KEY));
    }

    @Test
    void getAll() {
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(expectedAttributes);

        assertEquals(expectedAttributes, testSubject.getAll());
    }

    @Test
    void contains() {
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(expectedAttributes);

        assertTrue(testSubject.contains(ATTRIBUTE_KEY));
        assertFalse(testSubject.contains("some-other-handler"));
    }

    @Test
    void mergedWith() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        Map<String, Object> testOtherAttributes = new HashMap<>();
        testOtherAttributes.put("some-other-key", 1729);
        SimpleHandlerAttributes testOther = new SimpleHandlerAttributes(testOtherAttributes);

        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        expectedAttributes.put("some-other-key", 1729);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        HandlerAttributes result = testSubject.mergedWith(testOther);

        assertEquals(expectedAttributes, result.getAll());
    }

    @Test
    void mergedWithReturnsThis() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        assertEquals(testSubject, testSubject.mergedWith(new SimpleHandlerAttributes(Collections.emptyMap())));
    }

    @Test
    void mergedWithReturnsAdditionalAttributes() {
        Map<String, Object> testOtherAttributes = new HashMap<>();
        testOtherAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        SimpleHandlerAttributes testOther = new SimpleHandlerAttributes(testOtherAttributes);

        SimpleHandlerAttributes expected = new SimpleHandlerAttributes(testOtherAttributes);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(Collections.emptyMap());

        assertEquals(expected, testSubject.mergedWith(testOther));
    }
}
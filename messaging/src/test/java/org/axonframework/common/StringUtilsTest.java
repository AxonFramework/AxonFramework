package org.axonframework.common;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link StringUtils}.
 *
 * @author Steven van Beelen
 */
class StringUtilsTest {

    @Test
    void testNonEmptyOrNull() {
        assertFalse(StringUtils.nonEmptyOrNull(""));
        assertFalse(StringUtils.nonEmptyOrNull(null));
        assertTrue(StringUtils.nonEmptyOrNull("some-string"));
    }

    @Test
    void emptyOrNullReturnsTrueForEmptyAndNullStrings() {
        assertTrue(StringUtils.emptyOrNull(""));
        assertTrue(StringUtils.emptyOrNull(null));
        assertFalse(StringUtils.emptyOrNull("some-string"));
    }
}
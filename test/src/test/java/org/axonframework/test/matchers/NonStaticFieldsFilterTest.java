package org.axonframework.test.matchers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonStaticFieldsFilterTest {

    @SuppressWarnings("unused")
    private static String staticField;
    @SuppressWarnings("unused")
    private String nonStaticField;

    @Test
    void acceptNonTransientField() throws Exception {
        assertTrue(NonStaticFieldsFilter.instance()
                           .accept(getClass().getDeclaredField("nonStaticField")));
    }

    @Test
    void rejectTransientField() throws Exception {
        assertFalse(NonStaticFieldsFilter.instance()
                            .accept(getClass().getDeclaredField("staticField")));
    }
}
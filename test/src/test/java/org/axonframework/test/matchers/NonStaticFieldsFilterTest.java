package org.axonframework.test.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NonStaticFieldsFilterTest {

    @SuppressWarnings("unused")
    private static String staticField;
    @SuppressWarnings("unused")
    private String nonStaticField;

    @Test
    public void testAcceptNonTransientField() throws Exception {
        assertTrue(NonStaticFieldsFilter.instance()
                           .accept(getClass().getDeclaredField("nonStaticField")));
    }

    @Test
    public void testRejectTransientField() throws Exception {
        assertFalse(NonStaticFieldsFilter.instance()
                            .accept(getClass().getDeclaredField("staticField")));
    }
}
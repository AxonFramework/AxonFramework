package org.axonframework.serializer;

import org.junit.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class FixedValueRevisionResolverTest {

    @Test
    public void testRevisionOf() throws Exception {
        assertEquals("test", new FixedValueRevisionResolver("test").revisionOf(Object.class));
    }
}

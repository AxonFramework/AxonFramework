package org.axonframework.axonserver.connector.util;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AxonFrameworkVersionResolver}
 *
 * @author Sara Pellegrini
 */
public class AxonFrameworkVersionResolverTest {


    @Test
    public void testMavenPriorityOverEnvProperty() {
        AxonFrameworkVersionResolver testSubject = new AxonFrameworkVersionResolver(
                () -> "4.2.1",
                property -> property.equals("AXON_FRAMEWORK_VERSION") ? "3.5" : null);

        assertEquals("4.2.1", testSubject.get());
    }

    @Test
    public void testEnvPropertyUsedAsFallback() {
        AxonFrameworkVersionResolver testSubject = new AxonFrameworkVersionResolver(
                () -> null,
                property -> property.equals("AXON_FRAMEWORK_VERSION") ? "3.5" : null);

        assertEquals("3.5", testSubject.get());
    }

    @Test
    public void testEmptyStringAsFallback() {
        AxonFrameworkVersionResolver testSubject = new AxonFrameworkVersionResolver(() -> null, property -> null);

        assertEquals("", testSubject.get());
    }
}
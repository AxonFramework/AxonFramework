package org.axonframework.axonserver.connector.util;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AxonFrameworkVersionResolver}
 *
 * @author Sara Pellegrini
 */
class AxonFrameworkVersionResolverTest {


    @Test
    void testMavenPriorityOverEnvProperty() {
        AxonFrameworkVersionResolver testSubject = new AxonFrameworkVersionResolver(
                () -> "4.2.1",
                property -> property.equals("AXON_FRAMEWORK_VERSION") ? "3.5" : null);

        assertEquals("4.2.1", testSubject.get());
    }

    @Test
    void testEnvPropertyUsedAsFallback() {
        AxonFrameworkVersionResolver testSubject = new AxonFrameworkVersionResolver(
                () -> null,
                property -> property.equals("AXON_FRAMEWORK_VERSION") ? "3.5" : null);

        assertEquals("3.5", testSubject.get());
    }

    @Test
    void testEmptyStringAsFallback() {
        AxonFrameworkVersionResolver testSubject = new AxonFrameworkVersionResolver(() -> null, property -> null);

        assertEquals("", testSubject.get());
    }
}
package org.axonframework.serializer;

import org.junit.*;

import java.io.Serializable;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SerialVersionUIDRevisionResolverTest {

    private SerialVersionUIDRevisionResolverTemp testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new SerialVersionUIDRevisionResolverTemp();
    }

    @Test
    public void testRevisionOfAnnotatedClass() throws Exception {
        assertEquals("7038084420164786502", testSubject.revisionOf(IsSerializable.class));
    }

    @Test
    public void testRevisionOfNonAnnotatedClass() throws Exception {
        assertEquals(null, testSubject.revisionOf(NotSerializable.class));
    }

    private static class IsSerializable implements Serializable {
        private static final long serialVersionUID = 7038084420164786502L;
    }

    private static class NotSerializable {

    }
}

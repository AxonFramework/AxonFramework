package org.axonframework.serializer;

import org.junit.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class AnnotationRevisionResolverTest {

    private AnnotationRevisionResolver testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AnnotationRevisionResolver();
    }

    @Test
    public void testRevisionOfAnnotatedClass() throws Exception {
        assertEquals("2.3-TEST", testSubject.revisionOf(WithAnnotation.class));
    }

    @Test
    public void testRevisionOfNonAnnotatedClass() throws Exception {
        assertEquals(null, testSubject.revisionOf(WithoutAnnotation.class));
    }

    @Revision("2.3-TEST")
    private class WithAnnotation {

    }

    private class WithoutAnnotation {

    }
}

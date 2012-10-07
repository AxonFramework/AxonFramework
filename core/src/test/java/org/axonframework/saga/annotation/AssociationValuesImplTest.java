package org.axonframework.saga.annotation;

import org.axonframework.saga.AssociationValue;
import org.junit.*;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AssociationValuesImplTest {

    private AssociationValuesImpl testSubject;
    private AssociationValue associationValue;

    @Before
    public void setUp() throws Exception {
        testSubject = new AssociationValuesImpl();
        associationValue = new AssociationValue("key", "value");
    }

    @Test
    public void testAddAssociationValue() throws Exception {
        testSubject.add(associationValue);

        assertEquals(1, testSubject.addedAssociations().size());
        assertTrue(testSubject.removedAssociations().isEmpty());
    }

    @Test
    public void testAddAssociationValue_AddedTwice() throws Exception {
        testSubject.add(associationValue);
        testSubject.commit();
        testSubject.add(associationValue);
        assertTrue(testSubject.addedAssociations().isEmpty());
        assertTrue(testSubject.removedAssociations().isEmpty());
    }

    @Test
    public void testRemoveAssociationValue() {
        assertTrue(testSubject.add(associationValue));
        testSubject.commit();
        assertTrue(testSubject.remove(associationValue));
        assertTrue(testSubject.addedAssociations().isEmpty());
        assertEquals(1, testSubject.removedAssociations().size());
    }

    @Test
    public void testRemoveAssociationValue_NotInContainer() {
        testSubject.remove(associationValue);
        assertTrue(testSubject.addedAssociations().isEmpty());
        assertTrue(testSubject.removedAssociations().isEmpty());
    }

    @Test
    public void testAddAndRemoveEntry() {
        testSubject.add(associationValue);
        testSubject.remove(associationValue);

        assertTrue(testSubject.addedAssociations().isEmpty());
        assertTrue(testSubject.removedAssociations().isEmpty());
    }

    @Test
    public void testContaines() {
        assertFalse(testSubject.contains(associationValue));
        testSubject.add(associationValue);
        assertTrue(testSubject.contains(associationValue));
        assertTrue(testSubject.contains(new AssociationValue("key", "value")));
        testSubject.remove(associationValue);
        assertFalse(testSubject.contains(associationValue));
    }

    @Test
    public void testAsSet() {
        testSubject.add(associationValue);
        int t = 0;
        for (AssociationValue actual : testSubject.asSet()) {
            assertSame(associationValue, actual);
            t++;
        }
        assertEquals(1, t);
    }

    @Test
    public void testIterator() {
        testSubject.add(associationValue);
        Iterator<AssociationValue> iterator = testSubject.iterator();
        assertSame(associationValue, iterator.next());
        assertFalse(iterator.hasNext());
    }
}

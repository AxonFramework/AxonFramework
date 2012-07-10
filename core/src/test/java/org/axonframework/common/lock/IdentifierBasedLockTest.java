package org.axonframework.common.lock;

import org.junit.*;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class IdentifierBasedLockTest {

    private String identifier = "mockId";

    @Test
        public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
            IdentifierBasedLock manager = new IdentifierBasedLock();
            manager.obtainLock(identifier);
            manager.releaseLock(identifier);

            Field locksField = manager.getClass().getDeclaredField("locks");
            locksField.setAccessible(true);
            Map locks = (Map) locksField.get(manager);
            assertEquals("Expected lock to be cleaned up", 0, locks.size());
        }

        @Test
        public void testLockOnlyCleanedUpIfNoLocksAreHeld() {
            IdentifierBasedLock manager = new IdentifierBasedLock();

            assertFalse(manager.hasLock(identifier));

            manager.obtainLock(identifier);
            assertTrue(manager.hasLock(identifier));

            manager.obtainLock(identifier);
            assertTrue(manager.hasLock(identifier));

            manager.releaseLock(identifier);
            assertTrue(manager.hasLock(identifier));

            manager.releaseLock(identifier);
            assertFalse(manager.hasLock(identifier));
        }
}

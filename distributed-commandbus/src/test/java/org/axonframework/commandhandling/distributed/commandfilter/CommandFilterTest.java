package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandFilterTest {
    @Test
    public void testAcceptAll() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(new Object());

        assertTrue(AcceptAll.INSTANCE.test(testCommand));
        assertFalse(AcceptAll.INSTANCE.negate().test(testCommand));
        assertTrue(AcceptAll.INSTANCE.or(DenyAll.INSTANCE).test(testCommand));
        assertFalse(AcceptAll.INSTANCE.and(DenyAll.INSTANCE).test(testCommand));
    }

    @Test
    public void testDenyAll() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(new Object());

        assertFalse(DenyAll.INSTANCE.test(testCommand));
        assertTrue(DenyAll.INSTANCE.negate().test(testCommand));
        assertTrue(DenyAll.INSTANCE.or(AcceptAll.INSTANCE).test(testCommand));
        assertFalse(DenyAll.INSTANCE.and(AcceptAll.INSTANCE).test(testCommand));
    }

    @Test
    public void testCommandNameFilter() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>("acceptable", new Object(),
                Collections.emptyMap());

        CommandNameFilter filterAcceptable = new CommandNameFilter("acceptable");
        CommandNameFilter filterOther = new CommandNameFilter("other");

        assertTrue(filterAcceptable.test(testCommand));
        assertFalse(filterAcceptable.negate().test(testCommand));

        assertFalse(filterOther.test(testCommand));
        assertTrue(filterOther.negate().test(testCommand));

        assertTrue(filterOther.or(filterAcceptable).test(testCommand));
        assertTrue(filterAcceptable.or(filterOther).test(testCommand));
        assertFalse(filterOther.and(filterAcceptable).test(testCommand));
        assertFalse(filterAcceptable.and(filterOther).test(testCommand));

        assertFalse(filterOther.or(DenyAll.INSTANCE).test(testCommand));
        assertTrue(filterOther.or(AcceptAll.INSTANCE).test(testCommand));
        assertFalse(filterOther.and(DenyAll.INSTANCE).test(testCommand));
        assertFalse(filterOther.and(AcceptAll.INSTANCE).test(testCommand));

        assertTrue(filterAcceptable.or(DenyAll.INSTANCE).test(testCommand));
        assertTrue(filterAcceptable.or(AcceptAll.INSTANCE).test(testCommand));
        assertFalse(filterAcceptable.and(DenyAll.INSTANCE).test(testCommand));
        assertTrue(filterAcceptable.and(AcceptAll.INSTANCE).test(testCommand));
    }

    @Test
    public void testDenyCommandNameFilter() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>("acceptable", new Object(),
                Collections.emptyMap());

        DenyCommandNameFilter filterAcceptable = new DenyCommandNameFilter("acceptable");
        DenyCommandNameFilter filterOther = new DenyCommandNameFilter("other");

        assertFalse(filterAcceptable.test(testCommand));
        assertTrue(filterAcceptable.negate().test(testCommand));

        assertTrue(filterOther.test(testCommand));
        assertFalse(filterOther.negate().test(testCommand));

        assertTrue(filterOther.or(filterAcceptable).test(testCommand));
        assertTrue(filterAcceptable.or(filterOther).test(testCommand));
        assertFalse(filterOther.and(filterAcceptable).test(testCommand));
        assertFalse(filterAcceptable.and(filterOther).test(testCommand));

        assertTrue(filterOther.or(DenyAll.INSTANCE).test(testCommand));
        assertTrue(filterOther.or(AcceptAll.INSTANCE).test(testCommand));
        assertFalse(filterOther.and(DenyAll.INSTANCE).test(testCommand));
        assertTrue(filterOther.and(AcceptAll.INSTANCE).test(testCommand));

        assertFalse(filterAcceptable.or(DenyAll.INSTANCE).test(testCommand));
        assertTrue(filterAcceptable.or(AcceptAll.INSTANCE).test(testCommand));
        assertFalse(filterAcceptable.and(DenyAll.INSTANCE).test(testCommand));
        assertFalse(filterAcceptable.and(AcceptAll.INSTANCE).test(testCommand));
    }

}

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CommandCallbackRepositoryTest {
    private int successCounter;
    private int failCounter;

    @Before
    public void reset() {
        successCounter = 0;
        failCounter = 0;
    }

    @Test
    public void testCallback() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper = createWrapper("A");
        repository.store("A", commandCallbackWrapper);

        assertEquals(1, repository.callbacks().size());

        CommandCallbackWrapper<Object, Object, Object> fetchedCallback = repository.fetchAndRemove("A");
        assertEquals(commandCallbackWrapper, fetchedCallback);
        assertEquals(0, repository.callbacks().size());

        fetchedCallback.success(new Object());
        assertEquals(1, successCounter);
    }


    @Test
    public void testOverwriteCallback() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper = createWrapper("A");
        repository.store("A", commandCallbackWrapper);

        assertEquals(1, repository.callbacks().size());

        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper2 = createWrapper("A");
        repository.store("A", commandCallbackWrapper2);

        assertEquals(1, failCounter);
        assertTrue(repository.callbacks().containsValue(commandCallbackWrapper2));
        assertFalse(repository.callbacks().containsValue(commandCallbackWrapper));
    }

    @Test
    public void testCancelCallbacks() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        repository.store("A", createWrapper("A"));
        repository.store("B", createWrapper("A"));
        repository.store("C", createWrapper("A"));
        repository.store("D", createWrapper("B"));

        assertEquals(4, repository.callbacks().size());

        repository.cancelCallbacks("C");
        assertEquals(4, repository.callbacks().size());

        repository.cancelCallbacks("A");
        assertEquals(1, repository.callbacks().size());

        assertEquals(3, failCounter);
        assertEquals(0, successCounter);
    }

    private CommandCallbackWrapper<Object, Object, Object> createWrapper(Object sessionId) {
        return new CommandCallbackWrapper<>(
                sessionId, new GenericCommandMessage<>(new Object()),
                new CommandCallback<Object, Object>() {
                    @Override
                    public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                        successCounter++;
                    }

                    @Override
                    public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                        failCounter++;
                    }
                }
        );
    }
}

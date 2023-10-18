package org.axonframework.command.messaging;

import org.axonframework.messaging.Message;

public interface CommandResultMessage extends Message {

    boolean success();

    // Response styles:
    // 1. Void
    // 2. Response object
    // 3. 0..N events
    // 4. Response object AND 0..N events

}

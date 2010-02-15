package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Removed;

public class RemovedItemMessage {
    public var removed:Removed;

    public function RemovedItemMessage(removed:Removed) {
        this.removed = removed;
    }
}
}
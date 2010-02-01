package org.axonframework.examples.addressbook.messages {
import mx.collections.ArrayCollection;

public class AllContactsResultMessage {
    public var results:ArrayCollection;

    public function AllContactsResultMessage(results:ArrayCollection) {
        this.results = results;
    }
}
}
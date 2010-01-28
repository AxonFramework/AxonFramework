package org.axonframework.examples.addressbook.messages {
import mx.collections.ArrayCollection;

public class SearchResultMessage {
    public var results:ArrayCollection;

    public function SearchResultMessage(results:ArrayCollection) {
        this.results = results;
    }
}
}
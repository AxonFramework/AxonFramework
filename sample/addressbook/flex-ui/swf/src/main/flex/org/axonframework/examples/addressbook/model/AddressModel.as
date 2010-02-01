package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

import org.axonframework.examples.addressbook.messages.SearchResultMessage;

public class AddressModel {

    [Bindable]
    public var addresses:ArrayCollection;

    public function AddressModel() {
    }

    [MessageHandler]
    public function searchResults(message:SearchResultMessage):void {
        this.addresses = message.results;
    }

}
}
package org.axonframework.examples.addressbook.services {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;

public interface IAddressService {
    function search(message:SearchForAddressesMessage):AsyncToken;

    function create(message:NewAddressMessage):AsyncToken;
}
}
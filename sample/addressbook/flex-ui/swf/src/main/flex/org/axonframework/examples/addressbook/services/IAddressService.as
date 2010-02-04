package org.axonframework.examples.addressbook.services {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.messages.ShowContactsMessage;

public interface IAddressService {
    function search(message:SearchForAddressesMessage):AsyncToken;

    //    function create(message:NewAddressMessage):AsyncToken;

    function showContacts(message:ShowContactsMessage):AsyncToken;
}
}
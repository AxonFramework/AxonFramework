package org.axonframework.examples.addressbook.services {
import flash.events.EventDispatcher;

import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;
import mx.utils.ObjectUtil;

import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.messages.SearchResultMessage;
import org.axonframework.examples.addressbook.model.Address;
import org.axonframework.examples.addressbook.utils.MockServiceUtil;

public class AddressServiceStub extends EventDispatcher implements IAddressService {
    public var mockService:MockServiceUtil = new MockServiceUtil();

    [MessageDispatcher]
    public var dispatcher:Function;

    private var allAddresses:ArrayCollection = new ArrayCollection();

    public function AddressServiceStub() {
        allAddresses.addItem(Address.newAddress("kerkstraat 8", "Zoetermeer"));
        allAddresses.addItem(Address.newAddress("zwerflaan 18", "Amsterdam"));
        allAddresses.addItem(Address.newAddress("rijkestraat 23", "Bloemendaal"));
        allAddresses.addItem(Address.newAddress("kustweg 1", "Monster"));
    }

    [MessageHandler]
    public function search(message:SearchForAddressesMessage):AsyncToken {
        trace('Received a message SearchForAddressesMessageSearchForAddressesMessage ');
        var foundAddresses:ArrayCollection;
        var searchAddress:Address = message.address;

        if (searchAddress != null) {
            foundAddresses = new ArrayCollection();
            for each (var address:Address in allAddresses) {
                if (address.same(searchAddress)) {
                    foundAddresses.addItem(address);
                }
            }
        } else {
            foundAddresses = allAddresses;
        }
        dispatcher(new SearchResultMessage(ObjectUtil.copy(foundAddresses) as ArrayCollection));
        return mockService.createToken(ObjectUtil.copy(foundAddresses));
    }

}
}
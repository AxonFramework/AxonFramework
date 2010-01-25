package org.axonframework.examples.addressbook.actions {
import flash.events.EventDispatcher;

import mx.collections.ArrayCollection;

import mx.controls.Alert;

import mx.rpc.AsyncResponder;
import mx.rpc.AsyncToken;
import mx.rpc.events.FaultEvent;

import mx.rpc.events.ResultEvent;

import org.axonframework.examples.addressbook.events.AddressResultEvent;
import org.axonframework.examples.addressbook.model.Address;
import org.axonframework.examples.addressbook.services.IAddressService;

[ManagedEvents("searchResults")]
public class SearchAddressesAction extends EventDispatcher {

    private var addressSearchService:IAddressService;

    public function SearchAddressesAction() {
    }

    [Inject]
    public function init(addressSearchService:IAddressService):void {
        this.addressSearchService = addressSearchService;
        if (addressSearchService == null) {
            trace('Service is not injected into the action object')
        }
    }

    public function execute(searchAddress:Address):void {
        trace('executing the service');
        addressSearchService.search(searchAddress).addResponder(new AsyncResponder(searchAddresses_result, faultHandler));
    }

    private function searchAddresses_result(event:ResultEvent, token:AsyncToken):void {
        var addresses:ArrayCollection = event.result as ArrayCollection;
        dispatchEvent(new AddressResultEvent(AddressResultEvent.SEARCHRESULTS,addresses));
    }

    private function faultHandler(event:FaultEvent, token:Object = null):void {
        Alert.show(event.fault.faultString);
    }
}
}
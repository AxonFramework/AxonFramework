package org.axonframework.examples.addressbook.model {

[Bindable]
[RemoteClass(alias="org.axonframework.examples.addressbook.web.dto.RemovedDTO")]
public class Removed {
    public var contactIdentifier:String;
    public var addressType:String;

    public function Removed() {
    }
}
}
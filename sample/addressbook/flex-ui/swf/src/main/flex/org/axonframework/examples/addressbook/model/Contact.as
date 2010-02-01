package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

[Bindable]
[RemoteClass(alias="org.axonframework.examples.addressbook.web.dto.ContactDTO")]
public class Contact {

    public var name:String;
    public var uuid:String;
    public var addresses:ArrayCollection = new ArrayCollection();

    public function Contact() {
    }

    public static function newContact(name:String, uuid:String = ""):Contact {
        var contact:Contact = new Contact();
        contact.name = name;
        contact.uuid = uuid;
        return contact;
    }

    public function addAddress(address:Address):void {
        this.addresses.addItem(address);
    }
}
}
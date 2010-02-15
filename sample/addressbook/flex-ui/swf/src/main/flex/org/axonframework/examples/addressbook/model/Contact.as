package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

[Bindable]
[RemoteClass(alias="org.axonframework.examples.addressbook.web.dto.ContactDTO")]
public class Contact {

    public var name:String;
    public var uuid:String;
    public var detailsLoaded:Boolean;

    public var addresses:ArrayCollection = new ArrayCollection();

    public function Contact() {
    }

    public static function newContact(name:String, uuid:String = ""):Contact {
        var contact:Contact = new Contact();
        contact.name = name;
        contact.uuid = uuid;
        contact.detailsLoaded = false;
        return contact;
    }

    public function addAddress(address:Address):void {
        var i:int = 0;
        while (i < addresses.length) {
            if (addresses.getItemAt(i).type == address.type) {
                this.addresses.removeItemAt(i);
            }
            i++;
        }
        this.addresses.addItem(address);
    }

    public function removeAddress(addressType:String):void {
        var i:int = 0;
        while (i < addresses.length) {
            if (addresses.getItemAt(i).type == addressType) {
                this.addresses.removeItemAt(i);
            }
            i++;
        }
    }
}
}
package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.SelectContactMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class SelectContactCommand extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    public function SelectContactCommand() {
        super();
    }

    public function execute(contact:Contact):void {
        // TODO jettro : before going remote first check if we already have it
        dispatcher(new SelectContactMessage(contact));
        contactModel.selectedContact = contact;
    }
}
}
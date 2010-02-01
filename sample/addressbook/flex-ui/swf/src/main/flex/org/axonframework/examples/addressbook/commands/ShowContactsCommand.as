package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.ShowContactsMessage;

public class ShowContactsCommand  extends BaseCommand {
    public function ShowContactsCommand() {
        super();
    }

    public function execute():void {
        dispatcher(new ShowContactsMessage());
    }
}
}
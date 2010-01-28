package org.axonframework.examples.addressbook.commands {
public class BaseCommand {
    [MessageDispatcher]
    public var dispatcher:Function;

    public function BaseCommand() {
    }
}
}
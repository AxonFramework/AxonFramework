package org.axonframework.examples.addressbook.messages {
public class NotificationMessage {
    public var message:String;

    public function NotificationMessage(message:String) {
        this.message = message;
    }
}
}
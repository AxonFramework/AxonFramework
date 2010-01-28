package org.axonframework.examples.addressbook.messages {
public class ChangeViewMessage {
    public var stackId:String;

    public function ChangeViewMessage(stackId:String) {
        this.stackId = stackId;
    }
}
}
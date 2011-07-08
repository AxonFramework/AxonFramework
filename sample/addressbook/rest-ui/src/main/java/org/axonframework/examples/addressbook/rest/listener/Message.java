package org.axonframework.examples.addressbook.rest.listener;

/**
 * <p>Wrapper class used to push messages to redis with a value and a meaning.</p>
 *
 * @author Jettro Coenradie
 */
public class Message<T> {
    private T content;
    private String type;

    public Message(String type, T t) {
        this.content = t;
        this.type = type;
    }

    public T getContent() {
        return content;
    }

    public String getType() {
        return type;
    }
}

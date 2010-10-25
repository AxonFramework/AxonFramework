package org.axonframework.sample.app.command;

/**
 * @author Jettro Coenradie
 */
public interface ContactNameRepository {
    boolean claimContactName(String contactName);

    void cancelContactName(String contactName);
}

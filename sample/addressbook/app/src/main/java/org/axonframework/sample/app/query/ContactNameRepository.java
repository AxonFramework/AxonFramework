package org.axonframework.sample.app.query;

/**
 * @author Jettro Coenradie
 */
public interface ContactNameRepository {
    boolean claimContactName(String contactName);

    void cancelContactName(String contactName);
}

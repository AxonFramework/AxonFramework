package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.distributed.ConsistentHash;

/**
 * Receive updates from the JGroupsConnector when the consistent hash changes.
 * This is useful e.g. to clear domain caches that may contain stale versions of aggregates.
 *
 * @author Patrick Haas
 * @since 2.5
 */
public interface HashChangeListener {

    /**
     * Invoked when the memberships of a consistent hash have changed. The given <code>newHash</code> provides access
     * to the membership details, and can be used to define routing of messages.
     *
     * @param newHash The new hash
     * @see org.axonframework.commandhandling.distributed.ConsistentHash#getMember(String, String)
     * @see org.axonframework.commandhandling.distributed.ConsistentHash#getMembers()
     */
    void hashChanged(ConsistentHash newHash);
}

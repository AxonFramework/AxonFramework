package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.jgroups.Address;

import java.util.List;

/**
 * Receive updates from the JGroupsConnector when the consistent hash changes.
 * This is useful e.g. to clear domain caches that may contain stale versions of aggregates.
 *
 * @author Patrick Haas
 */
public interface HashChangeListener {

    void hashChanged(ConsistentHash newHash);
}

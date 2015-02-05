package org.axonframework.commandhandling.distributed.jgroups;

import org.jgroups.JChannel;

/**
 * JChannel factory used by the {@link JGroupsConnectorFactoryBean} to create the actual {@link JChannel}.
 *
 *
 * @author Patrick Haas
 */
public interface JChannelFactory {

    JChannel createChannel() throws Exception;
}

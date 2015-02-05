package org.axonframework.commandhandling.distributed.jgroups;

import org.jgroups.JChannel;

/**
 * @author Patrick Haas
 */
public class JGroupsXmlConfigurationChannelFactory implements JChannelFactory {
    private final String configuration;

    public JGroupsXmlConfigurationChannelFactory(String configuration) {
        this.configuration = configuration;
    }

    @Override
    public JChannel createChannel() throws Exception {
        return new JChannel(configuration);
    }
}

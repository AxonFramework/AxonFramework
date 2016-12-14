/*
 * Copyright (c) 2010-2015. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.jgroups.commandhandling;

import org.jgroups.JChannel;

/**
 * Implementation of a {@link JChannelFactory} that uses configuration from an xml file.
 *
 * @author Patrick Haas
 */
public class JGroupsXmlConfigurationChannelFactory implements JChannelFactory {

    private final String configurationFile;

    /**
     * Creates a {@link JChannelFactory} that uses configuration from a given xml file.
     *
     * @param configurationFile the configuration xml file name
     */
    public JGroupsXmlConfigurationChannelFactory(String configurationFile) {
        this.configurationFile = configurationFile;
    }

    @Override
    public JChannel createChannel() throws Exception {
        return new JChannel(configurationFile);
    }
}

/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axon.distributed")
public class DistributedCommandBusProperties {

    /**
     * Enables DistributedCommandBus configuration for this application
     */
    private boolean enabled = false;

    private JGroupsProperties jgroups = new JGroupsProperties();

    /**
     * Sets the loadFactor for this node to join with. The loadFactor sets the relative load this node will
     * receive compared to other nodes in the cluster. Defaults to 100.
     */
    private int loadFactor = 100;

    public boolean isEnabled() {
        return enabled || jgroups.isEnabled();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLoadFactor() {
        return loadFactor == 100 ? jgroups.getLoadFactor() : loadFactor;
    }

    public void setLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
    }

    public JGroupsProperties getJgroups() {
        return jgroups;
    }

    public void setJgroups(JGroupsProperties jgroups) {
        this.jgroups = jgroups;
    }

    public static class JGroupsProperties {

        private Gossip gossip = new Gossip();

        /**
         * Enables JGroups configuration for this application
         *
         * @deprecated JGroups specific 'enabled' property is deprecated in favor of the
         * DistributedCommandBusProperties' 'enabled' property
         */
        @Deprecated
        private boolean enabled = false;

        /**
         * The name of the JGroups cluster to connect to. Defaults to "Axon".
         */
        private String clusterName = "Axon";

        /**
         * The JGroups configuration file to use. Defaults to a TCP Gossip based configuration
         */
        private String configurationFile = "default_tcp_gossip.xml";

        /**
         * The address of the network interface to bind JGroups to. Defaults to a global IP address of this node.
         */
        private String bindAddr = "GLOBAL";

        /**
         * Sets the initial port to bind the JGroups connection to. If this port is taken, JGroups will find the
         * next available port.
         */
        private String bindPort = "7800";

        /**
         * Sets the loadFactor for this node to join with. The loadFactor sets the relative load this node will
         * receive compared to other nodes in the cluster. Defaults to 100.
         *
         * @deprecated JGroups specific 'loadFactor' property is deprecated in favor of the
         * DistributedCommandBusProperties' 'loadFactor' property
         */
        @Deprecated
        private int loadFactor = 100;

        public Gossip getGossip() {
            return gossip;
        }

        public void setGossip(Gossip gossip) {
            this.gossip = gossip;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public String getConfigurationFile() {
            return configurationFile;
        }

        public void setConfigurationFile(String configurationFile) {
            this.configurationFile = configurationFile;
        }

        public String getBindAddr() {
            return bindAddr;
        }

        public void setBindAddr(String bindAddr) {
            this.bindAddr = bindAddr;
        }

        public String getBindPort() {
            return bindPort;
        }

        public void setBindPort(String bindPort) {
            this.bindPort = bindPort;
        }

        public int getLoadFactor() {
            return loadFactor;
        }

        public void setLoadFactor(int loadFactor) {
            this.loadFactor = loadFactor;
        }

        public static class Gossip {

            /**
             * Whether to automatically attempt to start a Gossip Routers. The host and port of the Gossip server
             * are taken from the first define host in 'hosts'.
             */
            private boolean autoStart = false;

            /**
             * Defines the hosts of the Gossip Routers to connect to, in the form of host[port],...
             * <p>
             * If autoStart is set to {@code true}, the first host and port are used as bind address and bind port
             * of the Gossip server to start.
             * <p>
             * Defaults to localhost[12001].
             */
            private String hosts = "localhost[12001]";

            public boolean isAutoStart() {
                return autoStart;
            }

            public void setAutoStart(boolean autoStart) {
                this.autoStart = autoStart;
            }

            public String getHosts() {
                return hosts;
            }

            public void setHosts(String hosts) {
                this.hosts = hosts;
            }

        }

    }

}

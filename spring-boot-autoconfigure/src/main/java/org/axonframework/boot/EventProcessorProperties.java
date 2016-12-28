package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("axon.eventhandling")
public class EventProcessorProperties {

    /**
     * The configuration of each of the processors. The key is the name of the processor, the value represents the
     * settings to use for the processor with that name.
     */
    private Map<String, ProcessorSettings> processors = new HashMap<>();

    public Map<String, ProcessorSettings> getProcessors() {
        return processors;
    }

//    public void setProcessors(Map<String, ProcessorSettings> processors) {
//        this.processors = processors;
//    }

    public enum Mode {

        TRACKING,
        SUBSCRIBING
    }

    public static class ProcessorSettings {

        /**
         * Sets the source for this processor. Defaults to streaming from/subscribing to the Event Bus
         */
        private String source;

        /**
         * Indicates whether this processor should be Tracking, or Subscribing its source
         */
        private Mode mode = Mode.SUBSCRIBING;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }
    }
}

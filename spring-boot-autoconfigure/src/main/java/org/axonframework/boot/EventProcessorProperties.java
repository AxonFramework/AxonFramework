package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;

@ConfigurationProperties(prefix = "axon.eventhandling")
public class EventProcessorProperties {

    private HashMap<String, ProcessorSettings> processors = new HashMap<>();

    private String otherSetting;

    public String getOtherSetting() {
        return otherSetting;
    }

    public void setOtherSetting(String otherSetting) {
        this.otherSetting = otherSetting;
    }

    public HashMap<String, ProcessorSettings> getProcessors() {
        return processors;
    }

    public void setProcessors(HashMap<String, ProcessorSettings> processors) {
        this.processors = processors;
    }

    public static class ProcessorSettings {

        private String source;
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

    public enum Mode {

        TRACKING,
        SUBSCRIBING
    }
}

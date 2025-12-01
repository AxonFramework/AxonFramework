package org.axonframework.springboot.updates;

import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axon.update-check")
public class UpdateCheckConfiguration implements UsagePropertyProvider {

    /**
     * Indicates whether the update check should be disabled. Defaults to detecting this setting via system properties or
     * environment variables.
     * <p>
     * Unless disabled in any one of these locations, the update check will be enabled.
     */
    private boolean disabled;

    /**
     * The url to use to check for updates. Generally doesn't need to be changed, unless there is a local proxy, or for
     * test purposes. Defaults to the url defined via system properties or environment variables.
     */
    private String url;

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public Boolean getDisabled() {
        return disabled;
    }

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public int priority() {
        // higher than Environment Variables, but lower than Command Line Arguments
        return Integer.MAX_VALUE - Short.MAX_VALUE;
    }
}

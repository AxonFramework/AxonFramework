package io.axoniq.axonhub.client.processor;

/**
 * Created by Sara Pellegrini on 30/03/2018.
 * sara.pellegrini@gmail.com
 */
public class FakeAxonHubEventProcessorInfoSource implements AxonHubEventProcessorInfoSource {

    private int notifyCalls;

    @Override
    public void notifyInformation() {
        notifyCalls++;
    }

    public int notifyCalls() {
        return notifyCalls;
    }
}

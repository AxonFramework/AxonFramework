package io.axoniq.axonhub.client.processor.schedule;

import io.axoniq.axonhub.client.processor.AxonHubEventProcessorInfoSource;
import io.axoniq.axonhub.client.processor.AxonHubEventProcessorInfoSource.Fake;
import org.junit.*;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by Sara Pellegrini on 23/03/2018.
 * sara.pellegrini@gmail.com
 */
public class ScheduledEventProcessorInfoSourceTest {

    @Test
    public void notifyInformation() throws InterruptedException {
        Fake delegate = new Fake();
        ScheduledEventProcessorInfoSource scheduled = new ScheduledEventProcessorInfoSource(4,delegate);
        scheduled.start();
        TimeUnit.SECONDS.sleep(10);
        assertEquals(2, delegate.notifyCalls());
    }


}
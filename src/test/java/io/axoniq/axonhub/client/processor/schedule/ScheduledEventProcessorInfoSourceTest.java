package io.axoniq.axonhub.client.processor.schedule;

import io.axoniq.axonhub.client.processor.FakeAxonHubEventProcessorInfoSource;
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
        FakeAxonHubEventProcessorInfoSource delegate = new FakeAxonHubEventProcessorInfoSource();
        ScheduledEventProcessorInfoSource scheduled = new ScheduledEventProcessorInfoSource(50,30,delegate);
        scheduled.start();
        TimeUnit.MILLISECONDS.sleep(90);
        assertEquals(2, delegate.notifyCalls());
    }


}
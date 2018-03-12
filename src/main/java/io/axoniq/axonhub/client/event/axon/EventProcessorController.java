package io.axoniq.axonhub.client.event.axon;

import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventProcessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Created by Sara Pellegrini on 09/03/2018.
 * sara.pellegrini@gmail.com
 */
public class EventProcessorController {

    private final EventHandlingConfiguration eventHandlingConfiguration;

    private final Deque<Consumer<String>> pauseHandlers = new ArrayDeque<>();

    private final Deque<Consumer<String>> startHandlers = new ArrayDeque<>();

    public EventProcessorController(EventHandlingConfiguration eventHandlingConfiguration) {
        this.eventHandlingConfiguration = eventHandlingConfiguration;
    }

    private EventProcessor getEventProcessor(String processorName){
        return this.eventHandlingConfiguration
                .getProcessor(processorName)
                .orElseThrow(() -> new RuntimeException("Processor not found"));
    }

    public void pauseProcessor(String processor){
        //TODO check if it is intended only for tracking processor (pause method instead of shutDown)
        getEventProcessor(processor).shutDown();
        this.pauseHandlers.forEach(consumer -> consumer.accept(processor));
    }

    public void startProcessor(String processor){
        getEventProcessor(processor).start();
        this.startHandlers.forEach(consumer -> consumer.accept(processor));
    }

    public void onPause(Consumer<String> consumer){
        this.pauseHandlers.add(consumer);
    }

    public void onStart(Consumer<String> consumer){
        this.startHandlers.add(consumer);
    }
}

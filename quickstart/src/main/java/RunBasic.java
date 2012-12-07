import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.eventstore.fs.SimpleEventFileResolver;
import org.axonframework.quickstart.ToDoEventHandler;
import org.axonframework.quickstart.ToDoItem;

import java.io.File;

/**
 * Setting up the basic ToDoItem sample with a simple command and event bus and a file based event store. The
 * configuration takes place in java code.
 *
 * @author Jettro Coenradie
 */
public class RunBasic {

    public static void main(String[] args) {
        // let's start with the Command Bus
        CommandBus commandBus = new SimpleCommandBus();

        // the CommandGateway provides a friendlier API to send commands
        CommandGateway commandGateway = new DefaultCommandGateway(commandBus);

        // we'll store Events on the FileSystem, in the "events" folder
        EventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(new File("./events")));

        // a Simple Event Bus will do
        EventBus eventBus = new SimpleEventBus();

        // we need to configure the repository
        EventSourcingRepository<ToDoItem> repository = new EventSourcingRepository<ToDoItem>(ToDoItem.class);
        repository.setEventStore(eventStore);
        repository.setEventBus(eventBus);

        // Axon needs to know that our ToDoItem Aggregate can handle commands
        AggregateAnnotationCommandHandler.subscribe(ToDoItem.class, repository, commandBus);

        // We register an event listener to see which events are created
        AnnotationEventListenerAdapter.subscribe(new ToDoEventHandler(), eventBus);

        // and let's send some Commands on the CommandBus.
        ToDoItemRunner runner = new ToDoItemRunner(commandGateway);
        runner.run();
    }
}

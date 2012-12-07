import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.quickstart.api.CreateToDoItemCommand;
import org.axonframework.quickstart.api.MarkCompletedCommand;

import java.util.UUID;

/**
 * Runner to be used by all different specifications: basic, basic with spring
 *
 * @author Jettro Coenradie
 */
public class ToDoItemRunner {
    private final CommandGateway commandGateway;

    public ToDoItemRunner(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public void run() {
        final String itemId1 = UUID.randomUUID().toString();
        final String itemId2 = UUID.randomUUID().toString();
        commandGateway.send(new CreateToDoItemCommand(itemId1, "Check if it really works!"));
        commandGateway.send(new CreateToDoItemCommand(itemId2, "Think about the next steps!"));
        commandGateway.send(new MarkCompletedCommand(itemId1));
    }
}

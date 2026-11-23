package org.example.website.platform.internal.tasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;
import org.example.website.platform.api.tasks.DeploymentTask;
import org.jboss.logging.Logger;

/** Finds the task that was asked for, runs it, and turns the result into an exit code. */
@ApplicationScoped
public class TaskRunner {

    /** The application serves requests: no task was asked for. */
    public static final int SERVE = -1;

    private static final Logger LOG = Logger.getLogger(TaskRunner.class);

    private final Iterable<DeploymentTask> tasks;

    @Inject
    TaskRunner(Instance<DeploymentTask> tasks) {
        this.tasks = tasks;
    }

    /** For tests, with the tasks given directly. */
    TaskRunner(Iterable<DeploymentTask> tasks, boolean direct) {
        this.tasks = tasks;
    }

    /** Returns {@link #SERVE}, 0 when the task worked, or 1 when it did not. */
    public int run(Optional<String> requested) {
        if (requested.isEmpty() || requested.get().isBlank()) {
            return SERVE;
        }
        String name = requested.get().strip();
        for (DeploymentTask task : tasks) {
            if (task.name().equals(name)) {
                try {
                    LOG.infof("Deployment task %s starts", name);
                    task.run();
                    LOG.infof("Deployment task %s finished", name);
                    return 0;
                } catch (RuntimeException e) {
                    // The exception type only: the message can hold values from the environment
                    LOG.errorf("Deployment task %s failed (%s)", name, e.getClass().getName());
                    return 1;
                }
            }
        }
        LOG.errorf("There is no deployment task with the name %s", printable(name));
        return 1;
    }

    /** The name comes from the environment, but is cleaned anyway before it is logged. */
    private static String printable(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9_-]", "?");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}

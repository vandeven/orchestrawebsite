package org.example.website.platform.api.tasks;

/**
 * Work that must happen once per deployment, such as setting up the master account (HOS-19). The
 * deployment pipeline runs it with the new version of the application before the traffic moves, by
 * starting the same image with {@code DEPLOYMENT_TASK=<name>}. The application then runs the task
 * and stops: the exit code says whether it worked. A task must be safe to run again.
 */
public interface DeploymentTask {

    /** The value of {@code DEPLOYMENT_TASK} that selects this task. */
    String name();

    /** Does the work. Throws when it did not work, so the deployment stops. */
    void run();
}

package org.example.website.platform.internal.tasks;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The start of the application. Normally it serves requests until it is stopped. When {@code
 * DEPLOYMENT_TASK} is set it runs that one task instead and stops with its exit code, so the
 * deployment pipeline can start the same image as a Cloud Run job (HOS-19).
 */
@QuarkusMain
public class TaskMain implements QuarkusApplication {

    @Inject TaskRunner runner;

    public static void main(String... args) {
        Quarkus.run(TaskMain.class, args);
    }

    @Override
    public int run(String... args) {
        Optional<String> task =
                ConfigProvider.getConfig().getOptionalValue("deployment.task", String.class);
        int result = runner.run(task);
        if (result == TaskRunner.SERVE) {
            Quarkus.waitForExit();
            return 0;
        }
        return result;
    }
}

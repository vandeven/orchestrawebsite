package org.example.website.platform.internal.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.LogRecord;
import org.example.website.TestLogs;
import org.example.website.platform.api.tasks.DeploymentTask;
import org.junit.jupiter.api.Test;

class TaskRunnerTest {

    private final List<String> ran = new ArrayList<>();

    private DeploymentTask task(String name, boolean fails) {
        return new DeploymentTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void run() {
                ran.add(name);
                if (fails) {
                    throw new IllegalStateException("secret value from the environment");
                }
            }
        };
    }

    private TaskRunner runner() {
        return new TaskRunner(
                List.of(task("one", false), task("two", false), task("bad", true)), true);
    }

    @Test
    void noTaskMeansTheApplicationServes() {
        assertEquals(TaskRunner.SERVE, runner().run(Optional.empty()));
        assertEquals(TaskRunner.SERVE, runner().run(Optional.of("  ")));
        assertTrue(ran.isEmpty());
    }

    @Test
    void theRequestedTaskRunsAloneAndSucceeds() {
        assertEquals(0, runner().run(Optional.of("two")));
        assertEquals(List.of("two"), ran);
    }

    @Test
    void aFailingTaskGivesExitCode1AndLogsNoMessage() {
        List<LogRecord> logs =
                TestLogs.capture(() -> assertEquals(1, runner().run(Optional.of("bad"))));
        assertTrue(logs.stream().anyMatch(r -> TestLogs.everything(r).contains("failed")));
        assertTrue(logs.stream().noneMatch(r -> TestLogs.everything(r).contains("secret value")));
    }

    @Test
    void anUnknownTaskIsAFailureAndNothingRuns() {
        List<LogRecord> logs =
                TestLogs.capture(() -> assertEquals(1, runner().run(Optional.of("nope\nforged"))));
        assertTrue(ran.isEmpty());
        assertTrue(logs.stream().noneMatch(r -> TestLogs.everything(r).contains("\nforged")));
    }
}

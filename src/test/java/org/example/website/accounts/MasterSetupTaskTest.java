package org.example.website.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.example.website.platform.internal.tasks.TaskRunner;
import org.junit.jupiter.api.Test;

/** The master setup can be started as a deployment task by its name (HOS-19). */
@QuarkusTest
class MasterSetupTaskTest {

    @Inject TaskRunner runner;

    @Test
    void theMasterSetupRunsAsATaskAndCanRunAgain() {
        assertEquals(0, runner.run(Optional.of("master-setup")));
        assertEquals(0, runner.run(Optional.of("master-setup")));
    }
}

package org.example.website.accounts.internal.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.example.website.platform.api.tasks.DeploymentTask;

/**
 * Makes the master account match the environment once per deployment, as a task started with {@code
 * DEPLOYMENT_TASK=master-setup} (HOS-19, USR-15).
 */
@ApplicationScoped
class MasterSetupTask implements DeploymentTask {

    @Inject MasterAccountSetup setup;

    @Override
    public String name() {
        return "master-setup";
    }

    @Override
    public void run() {
        setup.ensureMasterAccount();
    }
}

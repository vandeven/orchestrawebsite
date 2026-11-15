# Runbook

[← Back to plan](PLAN.md)

Short instructions for emergencies, so someone other than the original developer can follow them. Values in capitals (for example `SERVICE`) are filled in when the Google Cloud project is set up. This file is kept up to date whenever the setup changes (see [plan](PLAN.md#operations-and-reliability)).

## Who can do this
The Google Cloud project has at least two owners (see [hosting](hosting.md), HOS-33), both with two-step verification. The names will be provided later and written here.

## The website is down
1. Check the alert email from the uptime check and the Google Cloud status page.
2. Open Cloud Run, look at the service logs (filter on severity ERROR) and at the latest deployment.
3. If the problem started with a deployment, roll back (next section).
4. If Google Cloud has an incident, wait; there is nothing to fix on our side.

## Roll back a bad deployment
1. List the revisions: `gcloud run revisions list --service SERVICE --region europe-west4`.
2. Send all traffic to the last good revision: `gcloud run services update-traffic SERVICE --region europe-west4 --to-revisions=REVISION=100`.
3. Check the website, then fix the problem in the repository. Do not merge automatic updates until it is fixed (see [automatic updates](automatic-updates.md), UPD-11).

## Restore data from a backup
1. Find the export in the private backup bucket (HOS-10, retention 30 days).
2. Import it into Firestore: `gcloud firestore import gs://BACKUP_BUCKET/EXPORT_PATH`. This overwrites documents with the same IDs, so check which collections you need first.
3. Files removed by mistake from the member files bucket can be restored within the soft delete period: `gcloud storage restore gs://BUCKET/OBJECT` (HOS-16). Deleted images cannot be restored (HOS-22).
4. The restore is tried before launch and every year after (see [security hardening](security-hardening.md), HRD-14).

## Set up or rebuild the infrastructure from the repository
These are the steps that cannot be code (see [infrastructure](infrastructure.md), INF-9). Do them in this order, with an owner account that has two-step verification.
1. Create the Google Cloud project, link billing and set the budget alert contact.
2. Create the private state bucket for OpenTofu in `europe-west4` (versioning on, public access prevention on).
3. Run the first `tofu apply` from `infra/` with your own credentials. This creates everything else, including the keyless GitHub login (Workload Identity Federation).
4. Add the secret values to the empty Secret Manager entries: `PASSWORD_PEPPER` (at least 32 random characters), `MASTER_PASSWORD` and the Brevo API key.
5. Let the application pipeline deploy the first version (see [hosting](hosting.md), HOS-6) and run the master account setup job.
6. Check the website, the uptime check and the alerts. Restore data from a backup if you are rebuilding after a disaster (see "Restore data from a backup").

## Infrastructure drift alert
The weekly drift check failed: the real project differs from the code in `infra/`. Look at the plan in the workflow run. Either put the console change in the code through a pull request, or revert it. Do not ignore it: the policy checks only protect what is in the code.

## Back up the music files by hand
The member files bucket is not backed up automatically (see the [instructions](instructions.md)). Admins keep the original files; the owner can also copy the bucket: `gcloud storage cp --recursive gs://FILES_BUCKET ./files-backup`. Do this now and then, and always before big changes. The images bucket can be copied the same way.

## Nobody can log in (for example the pepper was lost or changed)
1. Run the master account setup job again (HOS-19, HOS-20). The master can log in again.
2. Log in as master and resend the password recovery email to every user on the user management page (USR-6). Old passwords no longer work.

## The master account does not work
Run the master account setup job again (HOS-19). It sets the master account to match `MASTER_EMAIL` and `MASTER_PASSWORD`.

## Someone asks to see or remove their data
1. Reply within one month (see [legal pages](legal-pages.md), LEG-5).
2. To remove: an admin removes the user on the user management page. Logs and backups expire within 30 days.
3. To show their data: export the user's record from Firestore, and search the logs for their user ID or email address (30 days of history).

## Find out who changed something
Open Logs Explorer in Google Cloud and use the saved queries for the audit trail ([logging](logging.md), LGG-15). Entries are kept for 30 days.

## Suspected break-in
1. Roll back to the last revision you trust.
2. Change the secrets `MASTER_PASSWORD` and the Brevo API key in Secret Manager and deploy. Changing `PASSWORD_PEPPER` logs everyone out and invalidates all passwords, which is then followed by the steps in "Nobody can log in".
3. Check the audit trail and the alerts, then report to the people involved. Personal data leaks must be reported to the Dutch Data Protection Authority within 72 hours if there is a risk for people.

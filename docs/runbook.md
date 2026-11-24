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

## A song or a file was deleted by mistake
Files of a deleted song or a removed file are gone from the website at once, but the bucket keeps them for **7 days** (soft delete, [hosting](hosting.md), HOS-16). The songs and file details in the database are not kept: only the files can come back, and the song has to be added again by hand.
1. Find the file: `gcloud storage ls --soft-deleted gs://orchestrawebsite-1052386389794-files/songs/`. The names are random IDs and the file type is the ending (`.pdf`, `.mp3`, `.txt`).
2. Restore it: `gcloud storage restore gs://BUCKET/songs/FILE_ID.pdf#GENERATION` (use the generation number from the list).
3. Add the song in Song management and add the restored file again by uploading it (download it first with `gcloud storage cp`). The restored copy in the bucket is not linked to any song, so remove it afterwards.
Images in texts have **no** soft delete (HOS-22): a deleted image is gone for good.

## Restore data from a backup
The database has Firestore's own scheduled backups (every Sunday, kept 30 days, [hosting](hosting.md), HOS-10). A restore always creates a **new** database next to the live one; nothing is overwritten. **These steps have to be tried once before launch (see below): until then they are untested.**
1. List the backups: `gcloud firestore backups list --location=europe-west4`. Note the name of the backup you want (`projects/orchestrawebsite/locations/europe-west4/backups/BACKUP_ID`).
2. Restore it into a new database: `gcloud firestore databases restore --source-backup=BACKUP_NAME --destination-database=restored-YYYYMMDD`.
3. Look at what you need in the console (Firestore, choose the database `restored-YYYYMMDD`). If you only need some documents, copy them by hand or export the collection from the restored database to a temporary bucket and import it into `(default)`: `gcloud firestore export gs://TEMP_BUCKET/PATH --database=restored-YYYYMMDD --collection-ids=COLLECTION` and then `gcloud firestore import gs://TEMP_BUCKET/PATH --database="(default)"` (this overwrites documents with the same IDs, so check which collections you need first). Delete the temporary bucket afterwards.
4. If the whole live database is gone: restore into a new database, switch the application to it (the `FIRESTORE_DATABASE` setting, once the application has it), and rebuild `(default)` later.
5. Delete the restored database when you are done: `gcloud firestore databases delete --database=restored-YYYYMMDD` (the live database has deletion protection; a restored one does not).
6. Files removed by mistake from the member files bucket can be restored within the soft delete period: `gcloud storage restore gs://BUCKET/OBJECT` (HOS-16). Deleted images cannot be restored (HOS-22).
7. The restore is tried before launch and every year after (see [security hardening](security-hardening.md), HRD-14). Write the result, and any step that differs from this text, in the status log.

## Set up or rebuild the infrastructure from the repository
The complete first-time setup, with every command, is in the [manual](manual.md), Part A. These are the steps that cannot be code (see [infrastructure](infrastructure.md), INF-9). Do them in this order, with an owner account that has two-step verification.
1. Create the Google Cloud project, link billing and enable the APIs (manual A2). For a new project, use a new project ID and change it in `infra/prod.tfvars`, `infra/backend.hcl` and the manual.
2. Create the private state bucket for OpenTofu in `europe-west4` (versioning on, public access prevention on) and put its name in `infra/backend.hcl` and `prod.tfvars` (manual A3, step 1).
3. Set the three `TF_VAR_...` values, run `tofu init`, `plan` and the first `apply` from `infra/` with your own credentials (manual A3, steps 3 to 6). This creates everything else, including the keyless GitHub login (Workload Identity Federation).
4. Put the outputs and the three secrets in GitHub (manual A3, step 8).
5. Add the secret values to the empty Secret Manager entries: `PASSWORD_PEPPER` (at least 32 random characters), `MASTER_PASSWORD` and the Brevo API key (manual A4).
6. Let the application pipeline deploy the first version (see [hosting](hosting.md), HOS-6) and run the master account setup job.
7. Check the website, the uptime check and the alerts. Restore data from a backup if you are rebuilding after a disaster (see "Restore data from a backup").
A rebuild in a new project is tried once before launch and then once a year (see [instructions](instructions.md)).

## Infrastructure drift alert
The weekly drift check failed: the real project differs from the code in `infra/`. Look at the plan in the workflow run. Either put the console change in the code through a pull request, or revert it. Do not ignore it: the policy checks only protect what is in the code.

## Back up the music files by hand
The member files bucket is not backed up automatically (see the [instructions](instructions.md)). Admins keep the original files; the owner can also copy the bucket: `gcloud storage cp --recursive gs://FILES_BUCKET ./files-backup`. Do this now and then, and always before big changes. The images bucket can be copied the same way.

## Nobody can log in (for example the pepper was lost or changed)
1. Run the master account setup job again (HOS-19, HOS-20). The master can log in again.
2. Log in as master and resend the password recovery email to every user on the user management page (USR-6). Old passwords no longer work.

## The master account does not work
Run the master account setup job again (HOS-19). It sets the master account to match `MASTER_EMAIL` and `MASTER_PASSWORD`: `gcloud run jobs execute orchestra-website-master-setup --region europe-west4 --project orchestrawebsite --wait`. Read its log in the Cloud Run console (Jobs, Executions, Logs) if it fails. A normal deployment runs the same job, so a new `MASTER_PASSWORD` secret version takes effect with the next deployment.

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

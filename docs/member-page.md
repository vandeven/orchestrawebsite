# Member page and song management

[← Back to plan](PLAN.md)

## Goal
Give members one place to find everything they need for the songs the orchestra is working on now: sheet music, audio recordings and lyrics. Give admins a separate page to manage those songs and files. This is the core of the "tool for the people in the orchestra" purpose in the [plan](PLAN.md#purpose-and-audience).

This feature has two pages:

| Page | Access level | Purpose |
|------|--------------|---------|
| Member page | Members (and admins) | View active songs and download their files |
| Song management page | Admin only | Add songs, archive and unarchive them, and add and remove their files |

## User stories
- As a member, I want to see all songs we are working on so that I know what to practice.
- As a member, I want to download the sheet music, recordings and lyrics of a song so that I can practice at home.
- As an admin, I want to add a new song and upload its files so that members can start practicing it.
- As an admin, I want to archive a song we are no longer working on, and unarchive it later, so that the member page only shows current songs without losing the song and its files.
- As an admin, I want to remove a file that is no longer needed so that the member page stays up to date.
- As a member, I want to play recordings directly in the browser so that I can listen without downloading first.

## Functional requirements

### Member page
1. MEM-1: The member page is only accessible to logged-in members and admins. Anonymous visitors are sent to the login page (see [access control](PLAN.md#access-control-and-secure-section)).
2. MEM-2: The page lists all active songs the orchestra is working on. Members never see archived songs: archived songs are not listed, and the server does not give members download or playback links for files of archived songs, even if they know the song or file ID. Only admins see archived songs, on the song management page.
3. MEM-3: For each song, the page shows its files grouped by type:
   - Sheet music: one or more PDF files
   - Audio: one or more MP3 files
   - Lyrics: one or more plain text (.txt) files, available as a download only (not shown on the page)
4. MEM-4: Members can download each file. A download is only possible for a logged-in member; a download link shared with someone else stops working after a short time (see [hosting](hosting.md), file storage).
5. MEM-5: Each file shows a clear name (for example "Soprano", "Trumpet 1", "Full recording") so members can find the right part. All members can see all parts of every active song; files are not filtered per group, voice or instrument.
6. MEM-6: The page works well on phones, because members often open sheet music and recordings on their phone.
7. MEM-7: Members can play each MP3 file directly in the browser with a built-in audio player (play, pause, seek, and volume), as well as download it.
8. MEM-8: Playback keeps working when a member pauses for a long time. If the temporary link to the file has expired, the page gets a new link and continues from the same position.

### Song management page (admin)
9. MEM-9: The song management page is only accessible to admins.
10. MEM-10: Admins can add a song with at least a title. A new song is active.
11. MEM-11: The page shows all songs, with active and archived songs clearly separated (for example, two lists or a filter).
12. MEM-12: Admins can archive an active song, and unarchive an archived song, with a single click. Archiving only hides the song from the member page; the song and all its files are kept, so the song can be made current again at any time.
13. MEM-13: Admins can permanently delete a song (always with the confirmation dialog of the [plan](PLAN.md#confirming-destructive-actions)). Archiving is the normal way to take a song off the member page; deleting is for songs that will never be used again. Deleting removes the song and all its files. It requires a confirmation step that names the song and states that this cannot be undone.
14. MEM-14: Admins can add or remove files on both active and archived songs.
15. MEM-15: Admins can add one or more files to a song, of type sheet music, audio or lyrics, each with a display name.
16. MEM-16: Admins can remove a single file from a song, after a confirmation step (the dialog names the file and the song).
17. MEM-17: Uploads are checked for file type and size, both in the browser and on the server:
    - Sheet music: PDF (.pdf)
    - Audio: MP3 (.mp3)
    - Lyrics: plain text (.txt)
    - Maximum size: 15 MB per file
18. MEM-18: Uploads go directly from the browser to file storage, not through the web server (see [hosting](hosting.md)).
19. MEM-19: Changes made on the song management page are visible on the member page right away.
20. MEM-20: Admins can edit a song's title and notes, on both active and archived songs. The change is visible right away.
21. MEM-21: Admins can change the display name of a file. The file itself and its type stay the same.

## Content and data
- **Song:** ID, title, optional description or notes, status (active or archived), created date, date of the last status change, display order
- **File:** ID, song ID, type (sheet music, audio, lyrics), display name, original file name, content type, size, storage path, upload date. Who uploaded a file is not stored in the database; it is recorded in the audit trail in the logs (see [logging](logging.md), LGG-9).

Song and file records (metadata) are stored in the database. The files themselves are stored in Cloud Storage (see [hosting](hosting.md)).

## Input validation
All rules are checked in the browser and on the server (see [input validation](PLAN.md#input-validation)).

| Input | Rules |
|-------|-------|
| Song title | Required, 1 to 200 characters, leading and trailing spaces removed (also when editing, MEM-20) |
| Song description or notes | Optional, at most 2,000 characters, plain text |
| Song status | One of: active, archived |
| Display order | Whole number, 0 or higher |
| Song ID, file ID (in URLs and requests) | Required, expected ID format, must exist; for members, the song must be active (MEM-2) |
| File type | One of: sheet music, audio, lyrics |
| File display name | Required, 1 to 100 characters (also when renaming, MEM-21) |
| Uploaded file | Required; at most 15 MB; real content must match the file type: PDF for sheet music, MP3 for audio, plain UTF-8 text for lyrics. The file extension (.pdf, .mp3, .txt) must match as well. |
| Delete confirmation | The request must be an explicit confirmed delete (for example a separate delete request after the confirmation step), never a side effect of another request |

- The file type is checked by the file's content after the upload: the server checks the first bytes of the uploaded file (for example `%PDF` for PDF files, an MP3 frame or ID3 header for MP3 files, valid UTF-8 without binary data for text files). If the content does not match, the file is deleted and the admin sees a clear error.
- The original file name is only stored for display. It is never used as the storage path; the storage path is created by the server.

## Automatic tests
- **Access:** anonymous visitors are redirected to the login page; members can open the member page but not the song management page; admins and the master can open both (MEM-1, MEM-9).
- **Archived songs:** archived songs are not listed for members, and the server refuses download and playback links for files of archived songs, even when a member sends the IDs directly (MEM-2).
- **Files per song:** files are grouped by type with their display names (MEM-3, MEM-5).
- **Downloads and playback:** a member gets a working signed URL; the URL expires; the audio player gets a new link after expiry and continues at the same position (MEM-4, MEM-7, MEM-8).
- **Song management:** add a song (active by default), archive, unarchive, and delete with confirmation, including removal of its files (MEM-10 to MEM-13).
- **Files:** add files of each type to active and archived songs, and remove a file with confirmation (MEM-14 to MEM-16).
- **Validation:** every rule in the table above, with valid input, boundary values (for example exactly 200 characters and 201 characters, exactly 15 MB and just over), and invalid input. This includes a file renamed to `.pdf` that is not a PDF, and requests with unknown fields.
- **Changes visible right away:** a change on the song management page shows up on the member page (MEM-19).
- **Caching:** both pages send `Cache-Control: private` or `no-store`.
- **Phone layout and accessibility:** an end-to-end test at phone width, and an accessibility check with no WCAG 2.2 AA violations (MEM-6).
- **Editing (MEM-20, MEM-21):** an admin changes the title and notes of an active and an archived song and renames a file; the changes show on the member page right away (for active songs); invalid values are refused; a member cannot send these requests; each change is in the audit trail.
- **Confirmations:** deleting a song and removing a file each show the confirmation dialog with the name; Cancel and Escape change nothing; the confirm button performs the action; the request without the confirmation field, and as a `GET`, is refused (MEM-13, MEM-16).

## Additional requirements
- The member page and song management page are never cached publicly (`Cache-Control: private` or `no-store`).
- Sheet music and recordings may be protected by copyright, so files are never publicly accessible. They can only be downloaded through short-lived, signed links that are created after an access check.
- Downloads, uploads and audio playback go directly between the browser and Cloud Storage, so Cloud Run stays small and cheap.
- Files of archived songs stay in storage. At 15 MB per file at most, this costs very little, even after years of songs.
- When a song is permanently deleted, its files are removed from the bucket. They stay recoverable for the bucket's soft-delete period (see [hosting](hosting.md), HOS-16), as a safety net for mistakes.
- Songs, files and images use random UUIDs as IDs, files are downloaded as attachments with `nosniff`, and uploads are checked by content (see [security hardening](security-hardening.md), HRD-3 and HRD-4).
- The page shows a short reminder that the files are for members only, with a link to the terms of use (see [legal pages](legal-pages.md), LEG-9). A test checks that the reminder is on the page.

## Out of scope
- Notifications to members when songs or files are added, archived or unarchived.
- Showing lyrics on the page; lyrics are only downloaded.
- An archive list for members.
- Labels per group (band or choir) or a filter by group on the member page (for now).

## Open questions
- None at the moment.

## Status
Draft

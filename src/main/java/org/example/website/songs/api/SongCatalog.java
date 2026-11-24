package org.example.website.songs.api;

import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.users.UserSummary;

/**
 * What members see (MEM-1 to MEM-8): the active songs, and links to their files. Members never get
 * anything of an archived song, not even a link to a file whose ID they know (MEM-2).
 */
public interface SongCatalog {

    /** The active songs with their files, in the order admins set (MEM-2, MEM-3). */
    List<SongWithFiles> activeSongs(UserSummary viewer);

    /**
     * A signed address for a file, valid for 15 minutes. {@code inline} is for the audio player;
     * otherwise the file downloads as an attachment (HRD-4).
     *
     * @throws org.example.website.platform.api.errors.ResourceNotFound when there is no such file,
     *     or it belongs to an archived song and the viewer is not an admin
     * @throws org.example.website.platform.api.errors.AccessDenied for a viewer below member
     */
    FileLink link(UUID fileId, boolean inline, UserSummary viewer);
}

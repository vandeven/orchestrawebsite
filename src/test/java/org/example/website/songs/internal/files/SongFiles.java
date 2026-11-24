package org.example.website.songs.internal.files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test only: small made-up files of each type. */
public final class SongFiles {

    private SongFiles() {}

    public static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n"
                .getBytes(StandardCharsets.UTF_8);
    }

    /** An MP3 that starts with an ID3v2 tag. */
    public static byte[] mp3WithTag() {
        byte[] data = new byte[64];
        data[0] = 'I';
        data[1] = 'D';
        data[2] = '3';
        data[3] = 3;
        return data;
    }

    /**
     * An MP3 that starts straight with an MPEG audio frame (MPEG 1, layer III, 128 kbit/s, 44.1
     * kHz).
     */
    public static byte[] mp3Frame() {
        byte[] data = new byte[64];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xFB;
        data[2] = (byte) 0x90;
        return data;
    }

    public static byte[] lyrics() {
        return "Verse 1\nLa la la, café – déjà vu\r\n\tChorus\n".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] of(int size, byte[] start) {
        return Arrays.copyOf(start, size);
    }
}

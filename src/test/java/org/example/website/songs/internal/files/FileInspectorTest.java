package org.example.website.songs.internal.files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.example.website.songs.api.FileType;
import org.junit.jupiter.api.Test;

/** Files are checked by content, never by name or declared type (HRD-4). */
class FileInspectorTest {

    @Test
    void thePdfMustStartLikeAPdf() {
        assertTrue(FileInspector.matches(FileType.SHEET_MUSIC, SongFiles.pdf()));
        assertFalse(
                FileInspector.matches(
                        FileType.SHEET_MUSIC, "<html>%PDF-1.7 not at the start</html>".getBytes()));
        assertFalse(FileInspector.matches(FileType.SHEET_MUSIC, SongFiles.mp3WithTag()));
        assertFalse(FileInspector.matches(FileType.SHEET_MUSIC, SongFiles.lyrics()));
        assertFalse(
                FileInspector.matches(FileType.SHEET_MUSIC, "%PDF".getBytes()),
                "too short to be a PDF");
    }

    @Test
    void theMp3IsAnId3TagOrAnMpegFrame() {
        assertTrue(FileInspector.matches(FileType.AUDIO, SongFiles.mp3WithTag()));
        assertTrue(FileInspector.matches(FileType.AUDIO, SongFiles.mp3Frame()));
        assertFalse(FileInspector.matches(FileType.AUDIO, SongFiles.pdf()));
        assertFalse(
                FileInspector.matches(FileType.AUDIO, "RIFF....WAVEfmt ".getBytes()), "a WAV file");
        byte[] reservedBitrate = SongFiles.mp3Frame();
        reservedBitrate[2] = (byte) 0xF0;
        assertFalse(FileInspector.matches(FileType.AUDIO, reservedBitrate));
        byte[] wrongLayer = SongFiles.mp3Frame();
        wrongLayer[1] = (byte) 0xFF; // layer I
        assertFalse(FileInspector.matches(FileType.AUDIO, wrongLayer));
    }

    @Test
    void lyricsArePlainUtf8TextWithoutBinaryData() {
        assertTrue(FileInspector.matches(FileType.LYRICS, SongFiles.lyrics()));
        assertTrue(
                FileInspector.matches(
                        FileType.LYRICS,
                        "﻿With a byte order mark\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void binaryDataIsNotText() {
        assertFalse(
                FileInspector.matches(FileType.LYRICS, new byte[] {'a', 'b', 0, 'c', 'd'}),
                "a NUL byte");
        assertFalse(
                FileInspector.matches(
                        FileType.LYRICS, new byte[] {'a', 'b', 0x1B, '[', '3', '1', 'm'}),
                "an escape code");
        assertFalse(
                FileInspector.matches(
                        FileType.LYRICS, new byte[] {'a', 'b', (byte) 0xC3, (byte) 0x28, 'c'}),
                "not valid UTF-8");
        assertFalse(FileInspector.matches(FileType.LYRICS, SongFiles.mp3WithTag()));
        assertFalse(FileInspector.matches(FileType.LYRICS, new byte[] {'a', 'b'}), "too short");
    }

    @Test
    void emptyAndMissingContentIsNeverValid() {
        for (FileType type : FileType.values()) {
            assertFalse(FileInspector.matches(type, null));
            assertFalse(FileInspector.matches(type, new byte[0]));
        }
    }
}

package org.example.website.songs.internal.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentDispositionTest {

    @Test
    void aDownloadIsAnAttachmentWithTheDisplayNameAndTheRightExtension() {
        assertEquals(
                "attachment; filename=\"Trumpet 1.pdf\"; filename*=UTF-8''Trumpet%201.pdf",
                ContentDisposition.of("Trumpet 1", "pdf", false));
    }

    @Test
    void thePlayerGetsInline() {
        assertTrue(ContentDisposition.of("Full recording", "mp3", true).startsWith("inline;"));
    }

    @Test
    void specialCharactersCannotBreakOutOfTheHeader() {
        String value = ContentDisposition.of("a\"b\\c/d;e%f\r\nX-Evil: 1", "pdf", false);
        assertFalse(value.contains("\r"));
        assertFalse(value.contains("\n"));
        String ascii = value.substring(value.indexOf("filename=\"") + 10, value.indexOf("\";"));
        assertFalse(ascii.contains("\""));
        assertFalse(ascii.contains("\\"));
        assertFalse(ascii.contains("/"));
        assertFalse(ascii.contains(";"));
    }

    @Test
    void nonAsciiNamesAreEncodedAsUtf8() {
        String value = ContentDisposition.of("Café Sopraan", "txt", false);
        assertTrue(value.contains("filename*=UTF-8''Caf%C3%A9%20Sopraan.txt"), value);
        assertTrue(
                value.contains("filename=\"Caf_ Sopraan.txt\"")
                        || value.contains("filename=\"Caf__ Sopraan.txt\""),
                value);
    }
}

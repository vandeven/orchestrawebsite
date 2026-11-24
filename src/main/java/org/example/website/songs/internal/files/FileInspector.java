package org.example.website.songs.internal.files;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.example.website.songs.api.FileType;

/**
 * Looks at the content of an uploaded file to see whether it is what its type says (MEM input
 * rules, HRD-4): the name and the declared type are never trusted. Sheet music must start like a
 * PDF, audio like an MP3 (an ID3 tag or an MPEG frame), and lyrics must be plain UTF-8 text without
 * binary data.
 */
public final class FileInspector {

    private FileInspector() {}

    public static boolean matches(FileType type, byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        return switch (type) {
            case SHEET_MUSIC -> isPdf(data);
            case AUDIO -> isMp3(data);
            case LYRICS -> isText(data);
        };
    }

    private static boolean isPdf(byte[] d) {
        return d.length >= 8
                && d[0] == '%'
                && d[1] == 'P'
                && d[2] == 'D'
                && d[3] == 'F'
                && d[4] == '-';
    }

    private static boolean isMp3(byte[] d) {
        // An ID3v2 tag: "ID3", a version byte below 0xFF
        if (d.length >= 10 && d[0] == 'I' && d[1] == 'D' && d[2] == '3' && (d[3] & 0xFF) != 0xFF) {
            return true;
        }
        // An MPEG audio frame: 11 bits of sync, a real version, layer III, a real bitrate
        int b1 = d[1] & 0xFF;
        int b2 = d[2] & 0xFF;
        boolean sync = (d[0] & 0xFF) == 0xFF && (b1 & 0xE0) == 0xE0;
        boolean version = ((b1 >> 3) & 0x03) != 0x01; // 01 is reserved
        boolean layer3 = ((b1 >> 1) & 0x03) == 0x01;
        boolean bitrate = ((b2 >> 4) & 0x0F) != 0x0F && ((b2 >> 4) & 0x0F) != 0x00;
        boolean rate = ((b2 >> 2) & 0x03) != 0x03;
        return sync && version && layer3 && bitrate && rate;
    }

    private static boolean isText(byte[] d) {
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(d))
                            .toString();
        } catch (CharacterCodingException e) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowedControl = c == '\n' || c == '\r' || c == '\t' || c == '\f';
            if (Character.isISOControl(c) && !allowedControl) {
                return false; // binary data: a NUL, an escape code, ...
            }
        }
        return true;
    }
}

package org.example.website.songs.internal.files;

import java.nio.charset.StandardCharsets;

/**
 * The {@code Content-Disposition} of a download (HRD-4): an attachment with a safe file name made
 * from the display name, never from the name of the uploaded file or from the storage path.
 */
public final class ContentDisposition {

    private ContentDisposition() {}

    /**
     * {@code attachment; filename="..."; filename*=UTF-8''...}, or {@code inline} for the player.
     */
    public static String of(String displayName, String extension, boolean inline) {
        String name = displayName + "." + extension;
        StringBuilder ascii = new StringBuilder();
        for (char c : name.toCharArray()) {
            boolean safe = c >= 0x20 && c < 0x7F && "\"\\/;%".indexOf(c) < 0;
            ascii.append(safe ? c : '_');
        }
        String kind = inline ? "inline" : "attachment";
        return kind + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + percentEncode(name);
    }

    private static String percentEncode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean safe =
                    (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '-'
                            || c == '.'
                            || c == '_'
                            || c == '~';
            if (safe) {
                out.append((char) c);
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }
}

package org.example.website.content.internal.images;

import java.util.Optional;

/**
 * Looks at the first bytes of a file to see what it really is, and how many pixels it has (HRD-4):
 * the name and the declared type of a file are never trusted. Only JPEG, PNG and WebP are known,
 * SVG and everything else is refused. The image is not decoded, only its header is read.
 */
public final class ImageInspector {

    /** What a file is: the media type and the size in pixels. */
    public record Inspection(String contentType, int width, int height) {}

    private ImageInspector() {}

    public static Optional<Inspection> inspect(byte[] data) {
        if (data == null || data.length < 16) {
            return Optional.empty();
        }
        if (isPng(data)) {
            return png(data);
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return jpeg(data);
        }
        if (data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return webp(data);
        }
        return Optional.empty();
    }

    private static boolean isPng(byte[] d) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < signature.length; i++) {
            if ((d[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Inspection> png(byte[] d) {
        // The first chunk must be IHDR: length, "IHDR", width, height
        if (d.length < 24 || d[12] != 'I' || d[13] != 'H' || d[14] != 'D' || d[15] != 'R') {
            return Optional.empty();
        }
        return Optional.of(new Inspection("image/png", int32(d, 16), int32(d, 20)));
    }

    private static Optional<Inspection> jpeg(byte[] d) {
        int i = 2;
        while (i + 4 <= d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                return Optional.empty();
            }
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xFF) { // padding
                i++;
                continue;
            }
            if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                i += 2; // markers without a length
                continue;
            }
            int length = int16(d, i + 2);
            boolean startOfFrame =
                    marker >= 0xC0
                            && marker <= 0xCF
                            && marker != 0xC4
                            && marker != 0xC8
                            && marker != 0xCC;
            if (startOfFrame) {
                if (i + 9 > d.length) {
                    return Optional.empty();
                }
                return Optional.of(new Inspection("image/jpeg", int16(d, i + 7), int16(d, i + 5)));
            }
            if (length < 2) {
                return Optional.empty();
            }
            i += 2 + length;
        }
        return Optional.empty();
    }

    private static Optional<Inspection> webp(byte[] d) {
        if (d.length < 30) {
            return Optional.empty();
        }
        String chunk = new String(d, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8 ":
                // frame tag (3 bytes), start code 9D 01 2A, then 14-bit width and height
                if ((d[23] & 0xFF) != 0x9D || (d[24] & 0xFF) != 0x01 || (d[25] & 0xFF) != 0x2A) {
                    return Optional.empty();
                }
                return Optional.of(
                        new Inspection("image/webp", le16(d, 26) & 0x3FFF, le16(d, 28) & 0x3FFF));
            case "VP8L":
                if ((d[20] & 0xFF) != 0x2F) {
                    return Optional.empty();
                }
                int bits =
                        (d[21] & 0xFF)
                                | (d[22] & 0xFF) << 8
                                | (d[23] & 0xFF) << 16
                                | (d[24] & 0xFF) << 24;
                return Optional.of(
                        new Inspection(
                                "image/webp", (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1));
            case "VP8X":
                return Optional.of(new Inspection("image/webp", le24(d, 24) + 1, le24(d, 27) + 1));
            default:
                return Optional.empty();
        }
    }

    private static int int32(byte[] d, int at) {
        return (d[at] & 0xFF) << 24
                | (d[at + 1] & 0xFF) << 16
                | (d[at + 2] & 0xFF) << 8
                | (d[at + 3] & 0xFF);
    }

    private static int int16(byte[] d, int at) {
        return (d[at] & 0xFF) << 8 | (d[at + 1] & 0xFF);
    }

    private static int le16(byte[] d, int at) {
        return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8;
    }

    private static int le24(byte[] d, int at) {
        return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8 | (d[at + 2] & 0xFF) << 16;
    }
}

package org.example.website.content.internal.images;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Test only: small real and made-up image files. */
public final class ImageFiles {

    private ImageFiles() {}

    public static byte[] png(int width, int height) {
        return write("png", width, height);
    }

    public static byte[] jpeg(int width, int height) {
        return write("jpg", width, height);
    }

    private static byte[] write(String format, int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A PNG that only has a header: enough to say how big it claims to be. */
    public static byte[] pngHeaderOnly(int width, int height) {
        byte[] file = png(1, 1);
        putInt(file, 16, width);
        putInt(file, 20, height);
        return file;
    }

    /** A lossy WebP header (VP8) with a size, padded to a plausible length. */
    public static byte[] webpLossy(int width, int height) {
        byte[] data = new byte[40];
        riff(data, "VP8 ");
        data[20] = 0x10;
        data[23] = (byte) 0x9D;
        data[24] = 0x01;
        data[25] = 0x2A;
        data[26] = (byte) (width & 0xFF);
        data[27] = (byte) ((width >> 8) & 0x3F);
        data[28] = (byte) (height & 0xFF);
        data[29] = (byte) ((height >> 8) & 0x3F);
        return data;
    }

    /** A lossless WebP header (VP8L). */
    public static byte[] webpLossless(int width, int height) {
        byte[] data = new byte[40];
        riff(data, "VP8L");
        data[20] = 0x2F;
        int bits = (width - 1) | ((height - 1) << 14);
        data[21] = (byte) bits;
        data[22] = (byte) (bits >> 8);
        data[23] = (byte) (bits >> 16);
        data[24] = (byte) (bits >> 24);
        return data;
    }

    /** An extended WebP header (VP8X). */
    public static byte[] webpExtended(int width, int height) {
        byte[] data = new byte[40];
        riff(data, "VP8X");
        int w = width - 1;
        int h = height - 1;
        data[24] = (byte) w;
        data[25] = (byte) (w >> 8);
        data[26] = (byte) (w >> 16);
        data[27] = (byte) h;
        data[28] = (byte) (h >> 8);
        data[29] = (byte) (h >> 16);
        return data;
    }

    public static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj << /JS (app.alert(1)) >> endobj\n%%EOF 0123456789".getBytes();
    }

    public static byte[] svg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"><rect/></svg>      "
                .getBytes();
    }

    private static void riff(byte[] data, String chunk) {
        byte[] riff = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        System.arraycopy(riff, 0, data, 0, riff.length);
        byte[] name = chunk.getBytes();
        System.arraycopy(name, 0, data, 12, 4);
    }

    private static void putInt(byte[] data, int at, int value) {
        data[at] = (byte) (value >> 24);
        data[at + 1] = (byte) (value >> 16);
        data[at + 2] = (byte) (value >> 8);
        data[at + 3] = (byte) value;
    }
}

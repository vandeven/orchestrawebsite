package org.example.website.content.internal.images;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImageInspectorTest {

    private static ImageInspector.Inspection seen(byte[] data) {
        return ImageInspector.inspect(data).orElseThrow();
    }

    @Test
    void aPngIsRecognisedWithItsSize() {
        assertEquals(
                new ImageInspector.Inspection("image/png", 30, 20), seen(ImageFiles.png(30, 20)));
    }

    @Test
    void aJpegIsRecognisedWithItsSize() {
        assertEquals(
                new ImageInspector.Inspection("image/jpeg", 41, 17), seen(ImageFiles.jpeg(41, 17)));
    }

    @Test
    void allThreeKindsOfWebpAreRecognisedWithTheirSize() {
        assertEquals(
                new ImageInspector.Inspection("image/webp", 640, 480),
                seen(ImageFiles.webpLossy(640, 480)));
        assertEquals(
                new ImageInspector.Inspection("image/webp", 1000, 700),
                seen(ImageFiles.webpLossless(1000, 700)));
        assertEquals(
                new ImageInspector.Inspection("image/webp", 6000, 5999),
                seen(ImageFiles.webpExtended(6000, 5999)));
    }

    @Test
    void everythingElseIsNotAnImage() {
        assertTrue(ImageInspector.inspect(ImageFiles.pdf()).isEmpty(), "a PDF");
        assertTrue(ImageInspector.inspect(ImageFiles.svg()).isEmpty(), "an SVG");
        assertTrue(ImageInspector.inspect("GIF89a0123456789abcdef".getBytes()).isEmpty(), "a GIF");
        assertTrue(ImageInspector.inspect(new byte[0]).isEmpty());
        assertTrue(ImageInspector.inspect(null).isEmpty());
        assertTrue(ImageInspector.inspect(new byte[] {(byte) 0xFF, (byte) 0xD8}).isEmpty());
    }

    @Test
    void aTruncatedOrBrokenFileIsNotAnImageAndDoesNotCrash() {
        byte[] jpeg = ImageFiles.jpeg(10, 10);
        for (int length = 0; length < jpeg.length; length += 7) {
            ImageInspector.inspect(java.util.Arrays.copyOf(jpeg, length)); // must not throw
        }
        byte[] png = ImageFiles.png(10, 10);
        png[12] = 'X'; // not IHDR
        assertTrue(ImageInspector.inspect(png).isEmpty());
        byte[] webp = ImageFiles.webpLossy(10, 10);
        webp[23] = 0; // not the start code
        assertTrue(ImageInspector.inspect(webp).isEmpty());
    }

    @Test
    void aJpegWithAHugeSegmentLengthDoesNotLoopForever() {
        byte[] data = new byte[64];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xE1; // APP1 with a length that reaches past the end
        data[4] = (byte) 0xFF;
        data[5] = (byte) 0xFF;
        assertTrue(ImageInspector.inspect(data).isEmpty());
    }
}

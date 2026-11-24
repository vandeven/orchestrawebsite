package org.example.website.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.content.api.images.Image;
import org.example.website.content.api.images.ImageLibrary;
import org.example.website.content.api.images.UploadTicket;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.images.ImageFiles;
import org.example.website.content.internal.images.InMemoryImageStorage;
import org.example.website.content.internal.store.memory.InMemoryImages;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.internal.limits.InMemoryRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The image library (EDT-9 to EDT-17, HOS-14, HOS-22, HRD-4), with the image storage in memory. */
@QuarkusTest
class ImageLibraryTest {

    @Inject ImageLibrary library;
    @Inject InMemoryImageStorage storage;
    @Inject InMemoryImages images;
    @Inject InMemoryRateLimiter limits;
    @Inject EditableTexts texts;
    @Inject TestAccounts accounts;

    private UserSummary admin;

    @BeforeEach
    void start() {
        admin = accounts.newUser(Role.ADMIN);
        storage.clear();
        images.clear();
        limits.clear();
    }

    @AfterEach
    void stop() {
        for (TextKey key : TextKey.values()) {
            texts.clear(key, admin);
        }
        storage.clear();
        images.clear();
        limits.clear();
    }

    /** What the browser does: ask for an address, upload, report back. */
    private Image upload(byte[] file, String type, String alt, boolean decorative) {
        UploadTicket ticket = library.requestUpload(type, file.length, admin);
        storage.put(ticket.id() + "." + extension(type), file);
        return library.complete(ticket.id(), type, alt, decorative, admin);
    }

    private static String extension(String type) {
        return switch (type) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> "webp";
        };
    }

    // Asking for an upload address (HOS-14)

    @Test
    void theTicketHasASignedAddressAndTheHeadersToSend() {
        UploadTicket ticket = library.requestUpload("image/png", 5000, admin);
        assertTrue(ticket.uploadUrl().startsWith("https://"));
        assertEquals("image/png", ticket.headers().get("Content-Type"));
        assertEquals("1,10485760", ticket.headers().get("x-goog-content-length-range"));
    }

    @Test
    void onlyJpegPngAndWebpUpToTenMegabytesGetATicket() {
        for (String type :
                new String[] {
                    "image/svg+xml",
                    "image/gif",
                    "application/pdf",
                    "text/html",
                    "",
                    null,
                    "IMAGE/PNG"
                }) {
            assertThrows(
                    InvalidInput.class,
                    () -> library.requestUpload(type, 100, admin),
                    String.valueOf(type));
        }
        library.requestUpload("image/webp", ImageLibrary.MAX_BYTES, admin);
        assertThrows(
                InvalidInput.class,
                () -> library.requestUpload("image/png", ImageLibrary.MAX_BYTES + 1, admin));
        assertThrows(InvalidInput.class, () -> library.requestUpload("image/png", 0, admin));
        assertThrows(InvalidInput.class, () -> library.requestUpload("image/png", -5, admin));
    }

    @Test
    void ticketsAreRateLimitedPerPerson() {
        for (int i = 0; i < 100; i++) {
            library.requestUpload("image/png", 100, admin);
        }
        assertThrows(TooManyRequests.class, () -> library.requestUpload("image/png", 100, admin));
        library.requestUpload("image/png", 100, accounts.newUser(Role.ADMIN)); // someone else
    }

    // Checking what was uploaded (HRD-4)

    @Test
    void aGoodImageIsAddedWithItsRealSizeAndShownFromTheImageStorage() {
        Image image = upload(ImageFiles.png(40, 30), "image/png", "The band on stage", false);
        assertEquals(40, image.width());
        assertEquals(30, image.height());
        assertEquals("The band on stage", image.alt());
        assertEquals("https://images.example.test/" + image.id() + ".png", image.url());
        assertTrue(storage.isCacheable(image.id() + ".png"), "kept for a year by browsers");
        assertEquals(1, library.list().size());
    }

    @Test
    void jpegAndWebpWork() {
        assertEquals(10, upload(ImageFiles.jpeg(10, 12), "image/jpeg", "a", false).width());
        assertEquals(640, upload(ImageFiles.webpLossy(640, 480), "image/webp", "b", false).width());
    }

    @Test
    void aFileThatIsNotWhatItSaysIsRefusedAndDeletedFromStorage() {
        byte[][] files = {
            ImageFiles.pdf(),
            ImageFiles.svg(),
            ImageFiles.jpeg(10, 10),
            "plain text file, long enough".getBytes()
        };
        for (byte[] file : files) {
            UploadTicket ticket = library.requestUpload("image/png", file.length, admin);
            String name = ticket.id() + ".png";
            storage.put(name, file);
            assertThrows(
                    InvalidInput.class,
                    () -> library.complete(ticket.id(), "image/png", "x", false, admin));
            assertFalse(storage.exists(name), "removed right away");
        }
        assertTrue(library.list().isEmpty());
    }

    @Test
    void theSizeInPixelsHasExactBoundaries() {
        upload(ImageFiles.pngHeaderOnly(6000, 6000), "image/png", "ok", false);
        for (int[] size : new int[][] {{6001, 100}, {100, 6001}, {0, 10}, {10, 0}}) {
            UploadTicket ticket = library.requestUpload("image/png", 100, admin);
            storage.put(ticket.id() + ".png", ImageFiles.pngHeaderOnly(size[0], size[1]));
            assertThrows(
                    InvalidInput.class,
                    () -> library.complete(ticket.id(), "image/png", "x", false, admin));
            assertFalse(storage.exists(ticket.id() + ".png"));
        }
    }

    @Test
    void aFileOverTenMegabytesIsRefusedAndDeleted() {
        UploadTicket ticket = library.requestUpload("image/png", 1000, admin);
        byte[] big = new byte[(int) ImageLibrary.MAX_BYTES + 1];
        System.arraycopy(ImageFiles.png(10, 10), 0, big, 0, 40);
        storage.put(ticket.id() + ".png", big);
        assertThrows(
                InvalidInput.class,
                () -> library.complete(ticket.id(), "image/png", "x", false, admin));
        assertFalse(storage.exists(ticket.id() + ".png"));
    }

    @Test
    void reportingAnUploadThatNeverHappenedFails() {
        assertThrows(
                InvalidInput.class,
                () -> library.complete(UUID.randomUUID(), "image/png", "x", false, admin));
    }

    // Alternative text (EDT-12)

    @Test
    void theDescriptionIsRequiredUnlessTheImageIsDecorative() {
        byte[] file = ImageFiles.png(10, 10);
        for (String alt : new String[] {null, "", "   "}) {
            UploadTicket ticket = library.requestUpload("image/png", file.length, admin);
            storage.put(ticket.id() + ".png", file);
            assertThrows(
                    InvalidInput.class,
                    () -> library.complete(ticket.id(), "image/png", alt, false, admin),
                    String.valueOf(alt));
            assertTrue(
                    storage.exists(ticket.id() + ".png"),
                    "a missing description does not delete the upload");
        }
        assertEquals("", upload(file, "image/png", null, true).alt());
    }

    @Test
    void theDescriptionHasExactBoundariesAndIsOneLine() {
        byte[] file = ImageFiles.png(10, 10);
        assertEquals(250, upload(file, "image/png", "a".repeat(250), false).alt().length());
        UploadTicket ticket = library.requestUpload("image/png", file.length, admin);
        storage.put(ticket.id() + ".png", file);
        assertThrows(
                InvalidInput.class,
                () -> library.complete(ticket.id(), "image/png", "a".repeat(251), false, admin));
        assertThrows(
                InvalidInput.class,
                () -> library.complete(ticket.id(), "image/png", "two\nlines", false, admin));
        assertEquals(
                "trimmed",
                library.complete(ticket.id(), "image/png", "  trimmed  ", false, admin).alt());
    }

    @Test
    void reportingTheSameUploadTwiceGivesTheSameImage() {
        byte[] file = ImageFiles.png(10, 10);
        UploadTicket ticket = library.requestUpload("image/png", file.length, admin);
        storage.put(ticket.id() + ".png", file);
        Image first = library.complete(ticket.id(), "image/png", "x", false, admin);
        Image second = library.complete(ticket.id(), "image/png", "other", false, admin);
        assertEquals(first, second);
        assertEquals(1, library.list().size());
    }

    // The library (EDT-15, EDT-17)

    @Test
    void theLibraryListsNewestFirstAndMarksUnusedImages() {
        Image used = upload(ImageFiles.png(10, 10), "image/png", "used", false);
        Image unused = upload(ImageFiles.png(11, 11), "image/png", "unused", false);
        texts.save(TextKey.ABOUT, "<p><img src=\"" + used.url() + "\" alt=\"used\"></p>", admin);
        var list = library.list();
        assertEquals(2, list.size());
        var usedInfo =
                list.stream()
                        .filter(i -> i.image().id().equals(used.id()))
                        .findFirst()
                        .orElseThrow();
        var unusedInfo =
                list.stream()
                        .filter(i -> i.image().id().equals(unused.id()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(java.util.List.of(TextKey.ABOUT), usedInfo.usedIn());
        assertFalse(usedInfo.unused());
        assertTrue(unusedInfo.unused());
    }

    // Deleting (EDT-16, HOS-22)

    @Test
    void deletingRemovesTheFileAndTheDetailsForGood() {
        Image image = upload(ImageFiles.png(10, 10), "image/png", "x", false);
        library.delete(image.id(), admin);
        assertFalse(storage.exists(image.id() + ".png"));
        assertTrue(library.find(image.id()).isEmpty());
        assertThrows(ResourceNotFound.class, () -> library.delete(image.id(), admin));
    }

    @Test
    void theConfirmationCanNameTheTextsThatStillUseTheImage() {
        Image image = upload(ImageFiles.png(10, 10), "image/png", "x", false);
        texts.save(TextKey.AGENDA, "<img src=\"" + image.url() + "\" alt=\"x\">", admin);
        texts.save(TextKey.ABOUT, "<img src=\"" + image.url() + "\" alt=\"x\">", admin);
        var info = library.find(image.id()).orElseThrow();
        assertEquals(
                java.util.Set.of(TextKey.AGENDA, TextKey.ABOUT),
                new java.util.HashSet<>(info.usedIn()));
    }
}

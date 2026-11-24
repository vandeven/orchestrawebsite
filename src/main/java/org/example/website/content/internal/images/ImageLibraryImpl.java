package org.example.website.content.internal.images;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.audit.api.AuditAction;
import org.example.website.audit.api.AuditEntry;
import org.example.website.audit.api.AuditTrail;
import org.example.website.content.api.images.Image;
import org.example.website.content.api.images.ImageInfo;
import org.example.website.content.api.images.ImageLibrary;
import org.example.website.content.api.images.UploadTicket;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.store.ImageRecord;
import org.example.website.content.internal.store.ImageRepository;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.limits.RateLimiter;
import org.jboss.logging.Logger;

/**
 * The image library (EDT-9 to EDT-17). The browser uploads straight to storage with a signed
 * address; the server then looks at the file itself before it is used (HOS-14, HRD-4). Nothing
 * about an image is trusted until it has been checked by its content.
 */
@ApplicationScoped
public class ImageLibraryImpl implements ImageLibrary {

    private static final Logger LOG = Logger.getLogger(ImageLibraryImpl.class);

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private static final Duration UPLOAD_VALID = Duration.ofMinutes(15);

    @Inject ImageStorage storage;
    @Inject ImageRepository repository;
    @Inject EditableTexts texts;
    @Inject AuditTrail audit;
    @Inject RateLimiter limiter;
    @Inject ContentConfig config;
    @Inject Clock clock;

    @Override
    public UploadTicket requestUpload(String contentType, long sizeBytes, UserSummary actor) {
        String extension = extension(contentType);
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw new InvalidInput("The image must be from 1 byte to 10 MB");
        }
        var hit = limiter.hit("image-upload:" + actor.id(), Duration.ofHours(1));
        if (hit.count() > config.uploadsPerHour()) {
            LOG.warn("Upload addresses refused: asked too often");
            throw new TooManyRequests("Too many uploads", hit.untilReset().toSeconds());
        }
        UUID id = UUID.randomUUID();
        var signed =
                storage.signUpload(objectName(id, extension), contentType, MAX_BYTES, UPLOAD_VALID);
        return new UploadTicket(id, signed.url(), signed.headers());
    }

    @Override
    public Image complete(
            UUID id, String contentType, String alt, boolean decorative, UserSummary actor) {
        String extension = extension(contentType);
        String altText = decorative ? "" : cleanAlt(alt);
        Optional<ImageRecord> existing = repository.find(id);
        if (existing.isPresent()) {
            return image(existing.get()); // reported twice: the same image
        }
        String objectName = objectName(id, extension);
        byte[] data =
                storage.read(objectName, (int) MAX_BYTES)
                        .orElseThrow(() -> new InvalidInput("The image was not uploaded"));
        if (data.length > MAX_BYTES) {
            reject(objectName, "too large");
        }
        var inspection = ImageInspector.inspect(data);
        if (inspection.isEmpty() || !inspection.get().contentType().equals(contentType)) {
            reject(objectName, "not the type it says");
        }
        int width = inspection.get().width();
        int height = inspection.get().height();
        if (width < 1 || height < 1 || width > MAX_PIXELS || height > MAX_PIXELS) {
            reject(objectName, "wrong size in pixels");
        }
        storage.makeCacheable(objectName);
        ImageRecord record =
                new ImageRecord(
                        id,
                        objectName,
                        contentType,
                        width,
                        height,
                        data.length,
                        altText,
                        clock.instant());
        repository.save(record);
        LOG.infof("Image %s added (%dx%d, %d bytes)", id, width, height, data.length);
        audit.record(
                new AuditEntry(
                        AuditAction.IMAGE_UPLOAD,
                        person(actor),
                        null,
                        AuditEntry.Result.SUCCESS,
                        Map.of("image", id.toString())));
        return image(record);
    }

    @Override
    public List<ImageInfo> list() {
        List<TextKeyHtml> usage = usage();
        List<ImageInfo> result = new ArrayList<>();
        for (ImageRecord record : repository.findAll()) {
            result.add(new ImageInfo(image(record), usedIn(record, usage)));
        }
        return result;
    }

    @Override
    public Optional<ImageInfo> find(UUID id) {
        return repository
                .find(id)
                .map(record -> new ImageInfo(image(record), usedIn(record, usage())));
    }

    @Override
    public void delete(UUID id, UserSummary actor) {
        ImageRecord record =
                repository.find(id).orElseThrow(() -> new ResourceNotFound("No such image"));
        storage.delete(record.objectName());
        repository.delete(id);
        LOG.infof("Image %s deleted", id);
        audit.record(
                new AuditEntry(
                        AuditAction.IMAGE_DELETE,
                        person(actor),
                        null,
                        AuditEntry.Result.SUCCESS,
                        Map.of("image", id.toString())));
    }

    // Helpers

    private record TextKeyHtml(TextKey key, String html) {}

    private List<TextKeyHtml> usage() {
        List<TextKeyHtml> all = new ArrayList<>();
        for (TextKey key : TextKey.values()) {
            all.add(new TextKeyHtml(key, texts.get(key).html()));
        }
        return all;
    }

    private static List<TextKey> usedIn(ImageRecord record, List<TextKeyHtml> usage) {
        return usage.stream()
                .filter(text -> text.html().contains(record.objectName()))
                .map(TextKeyHtml::key)
                .toList();
    }

    private Image image(ImageRecord record) {
        return new Image(
                record.id(),
                config.imageBaseUrl() + record.objectName(),
                record.contentType(),
                record.width(),
                record.height(),
                record.sizeBytes(),
                record.alt(),
                record.uploadedAt());
    }

    private static String extension(String contentType) {
        String extension = contentType == null ? null : EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new InvalidInput("Only JPEG, PNG and WebP images are allowed");
        }
        return extension;
    }

    private static String objectName(UUID id, String extension) {
        return id + "." + extension;
    }

    private static String cleanAlt(String alt) {
        String text = alt == null ? "" : alt.strip();
        if (text.isEmpty() || text.length() > ALT_MAX) {
            throw new InvalidInput("The description must be 1 to 250 characters");
        }
        if (text.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidInput("The description must be on one line");
        }
        return text;
    }

    /** A file that is not acceptable is removed right away (HOS-14). */
    private void reject(String objectName, String why) {
        storage.delete(objectName);
        LOG.warnf("An uploaded image was refused and deleted (%s)", why);
        throw new InvalidInput("The file is not an acceptable image");
    }

    private static AuditEntry.Person person(UserSummary actor) {
        return new AuditEntry.Person(actor.id(), actor.email(), actor.role().name());
    }
}

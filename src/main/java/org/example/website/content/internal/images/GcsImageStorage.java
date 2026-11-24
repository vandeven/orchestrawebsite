package org.example.website.content.internal.images;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.jboss.logging.Logger;

/**
 * The images bucket in Cloud Storage (HOS-14, HOS-22). Upload addresses are signed without a key:
 * the application's service account signs through the IAM Credentials API (HOS-15). The client is
 * made when it is first used, so the application starts without it.
 */
@ApplicationScoped
@Typed(GcsImageStorage.class)
public class GcsImageStorage implements ImageStorage {

    private static final Logger LOG = Logger.getLogger(GcsImageStorage.class);

    @Inject ContentConfig config;

    private volatile Storage storage;

    private Storage storage() {
        Storage client = storage;
        if (client == null) {
            synchronized (this) {
                client = storage;
                if (client == null) {
                    client = StorageOptions.getDefaultInstance().getService();
                    storage = client;
                }
            }
        }
        return client;
    }

    @Override
    public SignedUpload signUpload(
            String objectName, String contentType, long maxBytes, Duration valid) {
        try {
            BlobInfo blob =
                    BlobInfo.newBuilder(BlobId.of(config.imagesBucket(), objectName))
                            .setContentType(contentType)
                            .build();
            Map<String, String> signed = Map.of("x-goog-content-length-range", "1," + maxBytes);
            URL url =
                    storage()
                            .signUrl(
                                    blob,
                                    valid.toSeconds(),
                                    TimeUnit.SECONDS,
                                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                                    Storage.SignUrlOption.withContentType(),
                                    Storage.SignUrlOption.withExtHeaders(signed),
                                    Storage.SignUrlOption.withV4Signature());
            return new SignedUpload(
                    url.toString(),
                    Map.of(
                            "Content-Type",
                            contentType,
                            "x-goog-content-length-range",
                            "1," + maxBytes));
        } catch (RuntimeException e) {
            LOG.errorf("An upload address could not be signed (%s)", e.getClass().getName());
            throw new ServiceUnavailable("The image storage is not available");
        }
    }

    @Override
    public Optional<byte[]> read(String objectName, int limit) {
        try {
            Blob blob = storage().get(BlobId.of(config.imagesBucket(), objectName));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            if (blob.getSize() != null && blob.getSize() > limit) {
                return Optional.of(new byte[limit + 1]); // too large: the content is not needed
            }
            return Optional.of(blob.getContent());
        } catch (RuntimeException e) {
            LOG.errorf("An image could not be read (%s)", e.getClass().getName());
            throw new ServiceUnavailable("The image storage is not available");
        }
    }

    @Override
    public void makeCacheable(String objectName) {
        try {
            Blob blob = storage().get(BlobId.of(config.imagesBucket(), objectName));
            if (blob != null) {
                blob.toBuilder()
                        .setCacheControl("public, max-age=31536000, immutable")
                        .build()
                        .update();
            }
        } catch (RuntimeException e) {
            LOG.errorf(
                    "The cache setting of an image could not be changed (%s)",
                    e.getClass().getName());
            throw new ServiceUnavailable("The image storage is not available");
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            storage().delete(BlobId.of(config.imagesBucket(), objectName));
        } catch (RuntimeException e) {
            LOG.errorf("An image could not be deleted (%s)", e.getClass().getName());
            throw new ServiceUnavailable("The image storage is not available");
        }
    }
}

package org.example.website.songs.internal.files;

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
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.songs.internal.config.SongsConfig;
import org.jboss.logging.Logger;

/**
 * The private files bucket in Cloud Storage (HOS-11 to HOS-15). Addresses are signed without a key:
 * the application's service account signs through the IAM Credentials API. The client is made when
 * it is first used, so the application starts without it.
 */
@ApplicationScoped
@Typed(GcsFileStorage.class)
public class GcsFileStorage implements FileStorage {

    private static final Logger LOG = Logger.getLogger(GcsFileStorage.class);

    @Inject SongsConfig config;

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
                    BlobInfo.newBuilder(BlobId.of(config.filesBucket(), objectName))
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
            throw new ServiceUnavailable("The file storage is not available");
        }
    }

    @Override
    public String signDownload(
            String objectName, String contentType, String contentDisposition, Duration valid) {
        try {
            BlobInfo blob =
                    BlobInfo.newBuilder(BlobId.of(config.filesBucket(), objectName)).build();
            Map<String, String> query =
                    Map.of(
                            "response-content-type", contentType,
                            "response-content-disposition", contentDisposition);
            return storage()
                    .signUrl(
                            blob,
                            valid.toSeconds(),
                            TimeUnit.SECONDS,
                            Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                            Storage.SignUrlOption.withQueryParams(query),
                            Storage.SignUrlOption.withV4Signature())
                    .toString();
        } catch (RuntimeException e) {
            LOG.errorf("A download address could not be signed (%s)", e.getClass().getName());
            throw new ServiceUnavailable("The file storage is not available");
        }
    }

    @Override
    public Optional<byte[]> read(String objectName, int limit) {
        try {
            Blob blob = storage().get(BlobId.of(config.filesBucket(), objectName));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            if (blob.getSize() != null && blob.getSize() > limit) {
                return Optional.of(new byte[limit + 1]); // too large: the content is not needed
            }
            return Optional.of(blob.getContent());
        } catch (RuntimeException e) {
            LOG.errorf("A file could not be read (%s)", e.getClass().getName());
            throw new ServiceUnavailable("The file storage is not available");
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            storage().delete(BlobId.of(config.filesBucket(), objectName));
        } catch (RuntimeException e) {
            LOG.errorf("A file could not be deleted (%s)", e.getClass().getName());
            throw new ServiceUnavailable("The file storage is not available");
        }
    }
}

package ru.posts.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

class S3MultipartUrlsTest {

    @Test
    void thresholdMatchesS3MinimumPartSize() {
        assertThat(S3MultipartUrls.usesMultipart(S3MultipartUrls.PART_SIZE_BYTES - 1))
                .isFalse();
        assertThat(S3MultipartUrls.usesMultipart(S3MultipartUrls.PART_SIZE_BYTES))
                .isTrue();
        assertThat(S3MultipartUrls.partCount(S3MultipartUrls.PART_SIZE_BYTES + 1))
                .isEqualTo(2);
    }

    @Test
    void beginPresignsEveryPartWithRealUploadId() throws Exception {
        S3Client s3 = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        CreateMultipartUploadResponse created = mock(CreateMultipartUploadResponse.class);
        when(created.uploadId()).thenReturn("mpu-real-id");
        when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenReturn(created);

        List<Integer> signedParts = new ArrayList<>();
        when(presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenAnswer(invocation -> {
            UploadPartPresignRequest request = invocation.getArgument(0);
            UploadPartRequest part = request.uploadPartRequest();
            signedParts.add(part.partNumber());
            assertThat(part.uploadId()).isEqualTo("mpu-real-id");
            PresignedUploadPartRequest presigned = mock(PresignedUploadPartRequest.class);
            when(presigned.url())
                    .thenReturn(URI.create("http://minio:9090/posts-media/key?partNumber="
                                    + part.partNumber()
                                    + "&uploadId=mpu-real-id&X-Amz-Signature=sig"
                                    + part.partNumber())
                            .toURL());
            return presigned;
        });

        long size = S3MultipartUrls.PART_SIZE_BYTES * 2 + 10;
        S3MultipartUrls.Session session = S3MultipartUrls.begin(
                s3,
                presigner,
                "posts-media",
                "workspaces/ws/media/id/clip.mp4",
                "video/mp4",
                size,
                Duration.ofMinutes(15),
                "https://socialposts.ru/s3");

        assertThat(session.uploadId()).isEqualTo("mpu-real-id");
        assertThat(session.parts()).hasSize(3);
        assertThat(signedParts).containsExactly(1, 2, 3);
        assertThat(session.parts().get(0).url())
                .startsWith("https://socialposts.ru/s3/posts-media/key?")
                .contains("partNumber=1")
                .contains("X-Amz-Signature=sig1")
                .doesNotContain("upload-");
        assertThat(session.parts().get(2).url()).contains("partNumber=3");
        verify(presigner, times(3)).presignUploadPart(any(UploadPartPresignRequest.class));
    }

    @Test
    void quoteEtagKeepsExistingQuotes() {
        assertThat(S3MultipartUrls.quoteEtag("abc")).isEqualTo("\"abc\"");
        assertThat(S3MultipartUrls.quoteEtag("\"abc-2\"")).isEqualTo("\"abc-2\"");
    }
}

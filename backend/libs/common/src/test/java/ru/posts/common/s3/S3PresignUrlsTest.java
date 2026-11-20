package ru.posts.common.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class S3PresignUrlsTest {

    @Test
    void rewritesInternalMinioHostToGatewayPath() {
        String presigned = "http://minio:9090/posts-media/workspaces/w1/media/m1/file.png?X-Amz-Algorithm=AWS4";
        String publicBase = "http://100.102.9.56:3000/s3";
        assertThat(S3PresignUrls.toPublicUrl(presigned, publicBase))
                .isEqualTo(
                        "http://100.102.9.56:3000/s3/posts-media/workspaces/w1/media/m1/file.png?X-Amz-Algorithm=AWS4");
    }

    @Test
    void leavesUrlUnchangedWhenPublicBaseMissing() {
        String presigned = "http://minio:9090/posts-media/key";
        assertThat(S3PresignUrls.toPublicUrl(presigned, null)).isEqualTo(presigned);
    }

    @Test
    void detectsDockerInternalHosts() {
        assertThat(S3PresignUrls.isDockerInternalHost("minio")).isTrue();
        assertThat(S3PresignUrls.isDockerInternalHost("100.102.9.56")).isFalse();
    }
}

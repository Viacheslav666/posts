package ru.posts.grabber.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.grabber")
public class GrabberProperties {

    /** Feature flag for type=website (BE-17b). When false, API returns 404. */
    private boolean websiteEnabled = true;

    private Media media = new Media();

    public boolean isWebsiteEnabled() {
        return websiteEnabled;
    }

    public void setWebsiteEnabled(boolean websiteEnabled) {
        this.websiteEnabled = websiteEnabled;
    }

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media == null ? new Media() : media;
    }

    /** Re-upload of grabbed photos/videos into the target VK community. */
    public static class Media {

        /** Download + photos.saveWallPhoto into the target group instead of referencing the source. */
        private boolean reuploadPhotos = true;

        private long maxPhotoBytes = 20L * 1024 * 1024;

        /** Product rule: only public videos up to 50 MB are re-uploaded. */
        private long maxVideoBytes = 50L * 1024 * 1024;

        public boolean isReuploadPhotos() {
            return reuploadPhotos;
        }

        public void setReuploadPhotos(boolean reuploadPhotos) {
            this.reuploadPhotos = reuploadPhotos;
        }

        public long getMaxPhotoBytes() {
            return maxPhotoBytes;
        }

        public void setMaxPhotoBytes(long maxPhotoBytes) {
            this.maxPhotoBytes = maxPhotoBytes;
        }

        public long getMaxVideoBytes() {
            return maxVideoBytes;
        }

        public void setMaxVideoBytes(long maxVideoBytes) {
            this.maxVideoBytes = maxVideoBytes;
        }
    }
}

package ru.posts.grabber.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.grabber")
public class GrabberProperties {

    /** Feature flag for type=website (BE-17b). When false, API returns 404. */
    private boolean websiteEnabled = true;

    private Media media = new Media();

    /** Rewrite provider. Disabled until {@code posts.grabber.ai.enabled=true}. No API keys live here. */
    private final Ai ai = new Ai();

    /**
     * wall.post {@code action_button} is not a documented parameter we can verify from CI. Off until
     * {@code posts.grabber.action-button.enabled=true}.
     */
    private final ActionButton actionButton = new ActionButton();

    /**
     * {@code posts.grabber.postponed-repost.enabled}. Off until {@code wall.repost} publish_date is
     * confirmed on real VK. The publish path stays in code and runs only when this is true.
     */
    private PostponedRepost postponedRepost = new PostponedRepost();

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

    public Ai getAi() {
        return ai;
    }

    public ActionButton getActionButton() {
        return actionButton;
    }

    public boolean isPostponedRepostEnabled() {
        return postponedRepost != null && postponedRepost.isEnabled();
    }

    public PostponedRepost getPostponedRepost() {
        return postponedRepost;
    }

    public void setPostponedRepost(PostponedRepost postponedRepost) {
        this.postponedRepost = postponedRepost == null ? new PostponedRepost() : postponedRepost;
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

    public static class Ai {
        private boolean enabled = false;
        private long costKopecks = 100L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getCostKopecks() {
            return costKopecks;
        }

        public void setCostKopecks(long costKopecks) {
            this.costKopecks = costKopecks;
        }
    }

    public static class ActionButton {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PostponedRepost {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

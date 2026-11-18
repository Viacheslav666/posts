package ru.posts.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.media")
public class MediaProperties {

    private int prefetch = 10;
    private final Clamav clamav = new Clamav();
    private final Limits limits = new Limits();
    private int previewMaxEdge = 1280;

    public int getPrefetch() {
        return prefetch;
    }

    public void setPrefetch(int prefetch) {
        this.prefetch = prefetch;
    }

    public Clamav getClamav() {
        return clamav;
    }

    public Limits getLimits() {
        return limits;
    }

    public int getPreviewMaxEdge() {
        return previewMaxEdge;
    }

    public void setPreviewMaxEdge(int previewMaxEdge) {
        this.previewMaxEdge = previewMaxEdge;
    }

    public static class Clamav {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Limits {
        private long imageMaxBytes = 20L * 1024 * 1024;
        private long videoMaxBytes = 1024L * 1024 * 1024;
        private long docMaxBytes = 100L * 1024 * 1024;
        private long audioMaxBytes = 100L * 1024 * 1024;

        public long getImageMaxBytes() {
            return imageMaxBytes;
        }

        public void setImageMaxBytes(long imageMaxBytes) {
            this.imageMaxBytes = imageMaxBytes;
        }

        public long getVideoMaxBytes() {
            return videoMaxBytes;
        }

        public void setVideoMaxBytes(long videoMaxBytes) {
            this.videoMaxBytes = videoMaxBytes;
        }

        public long getDocMaxBytes() {
            return docMaxBytes;
        }

        public void setDocMaxBytes(long docMaxBytes) {
            this.docMaxBytes = docMaxBytes;
        }

        public long getAudioMaxBytes() {
            return audioMaxBytes;
        }

        public void setAudioMaxBytes(long audioMaxBytes) {
            this.audioMaxBytes = audioMaxBytes;
        }

        public long forKind(String kind) {
            return switch (kind) {
                case "image" -> imageMaxBytes;
                case "video" -> videoMaxBytes;
                case "audio" -> audioMaxBytes;
                default -> docMaxBytes;
            };
        }
    }
}

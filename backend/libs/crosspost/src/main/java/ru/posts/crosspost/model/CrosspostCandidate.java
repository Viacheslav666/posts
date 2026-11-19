package ru.posts.crosspost.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Normalized source post for filter/transform engines. */
public record CrosspostCandidate(
        String network,
        String remoteId,
        String text,
        List<Map<String, Object>> attachments,
        boolean markedAsAds,
        boolean hasErid,
        boolean isRepost,
        boolean fromGroup,
        boolean hasPoll,
        boolean hasGeo,
        boolean hasVideo,
        boolean hasClip,
        boolean hasAudio,
        boolean hasImage,
        boolean hasLinkAttachment,
        boolean hasSnippet,
        boolean hasArticle,
        Instant publishedAt,
        Map<String, Object> rawMeta) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String network;
        private String remoteId;
        private String text = "";
        private List<Map<String, Object>> attachments = List.of();
        private boolean markedAsAds;
        private boolean hasErid;
        private boolean isRepost;
        private boolean fromGroup;
        private boolean hasPoll;
        private boolean hasGeo;
        private boolean hasVideo;
        private boolean hasClip;
        private boolean hasAudio;
        private boolean hasImage;
        private boolean hasLinkAttachment;
        private boolean hasSnippet;
        private boolean hasArticle;
        private Instant publishedAt;
        private Map<String, Object> rawMeta = Map.of();

        public Builder network(String network) {
            this.network = network;
            return this;
        }

        public Builder remoteId(String remoteId) {
            this.remoteId = remoteId;
            return this;
        }

        public Builder text(String text) {
            this.text = text == null ? "" : text;
            return this;
        }

        public Builder attachments(List<Map<String, Object>> attachments) {
            this.attachments = attachments == null ? List.of() : attachments;
            return this;
        }

        public Builder markedAsAds(boolean markedAsAds) {
            this.markedAsAds = markedAsAds;
            return this;
        }

        public Builder hasErid(boolean hasErid) {
            this.hasErid = hasErid;
            return this;
        }

        public Builder isRepost(boolean isRepost) {
            this.isRepost = isRepost;
            return this;
        }

        public Builder fromGroup(boolean fromGroup) {
            this.fromGroup = fromGroup;
            return this;
        }

        public Builder hasPoll(boolean hasPoll) {
            this.hasPoll = hasPoll;
            return this;
        }

        public Builder hasGeo(boolean hasGeo) {
            this.hasGeo = hasGeo;
            return this;
        }

        public Builder hasVideo(boolean hasVideo) {
            this.hasVideo = hasVideo;
            return this;
        }

        public Builder hasClip(boolean hasClip) {
            this.hasClip = hasClip;
            return this;
        }

        public Builder hasAudio(boolean hasAudio) {
            this.hasAudio = hasAudio;
            return this;
        }

        public Builder hasImage(boolean hasImage) {
            this.hasImage = hasImage;
            return this;
        }

        public Builder hasLinkAttachment(boolean hasLinkAttachment) {
            this.hasLinkAttachment = hasLinkAttachment;
            return this;
        }

        public Builder hasSnippet(boolean hasSnippet) {
            this.hasSnippet = hasSnippet;
            return this;
        }

        public Builder hasArticle(boolean hasArticle) {
            this.hasArticle = hasArticle;
            return this;
        }

        public Builder publishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public Builder rawMeta(Map<String, Object> rawMeta) {
            this.rawMeta = rawMeta == null ? Map.of() : rawMeta;
            return this;
        }

        public CrosspostCandidate build() {
            return new CrosspostCandidate(
                    network,
                    remoteId,
                    text,
                    attachments,
                    markedAsAds,
                    hasErid,
                    isRepost,
                    fromGroup,
                    hasPoll,
                    hasGeo,
                    hasVideo,
                    hasClip,
                    hasAudio,
                    hasImage,
                    hasLinkAttachment,
                    hasSnippet,
                    hasArticle,
                    publishedAt,
                    rawMeta);
        }
    }
}

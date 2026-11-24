package ru.posts.grabber.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.grabber")
public class GrabberProperties {

    /** Feature flag for type=website (BE-17b). When false, API returns 404. */
    private boolean websiteEnabled = true;

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

    public boolean isPostponedRepostEnabled() {
        return postponedRepost != null && postponedRepost.isEnabled();
    }

    public PostponedRepost getPostponedRepost() {
        return postponedRepost;
    }

    public void setPostponedRepost(PostponedRepost postponedRepost) {
        this.postponedRepost = postponedRepost == null ? new PostponedRepost() : postponedRepost;
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

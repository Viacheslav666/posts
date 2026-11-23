package ru.posts.grabber.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.grabber")
public class GrabberProperties {

    /** Feature flag for type=website (BE-17b). When false, API returns 404. */
    private boolean websiteEnabled = true;

    /** Rewrite provider. Disabled until {@code posts.grabber.ai.enabled=true}. No API keys live here. */
    private final Ai ai = new Ai();

    /**
     * wall.post {@code action_button} is not a documented parameter we can verify from CI. Off until
     * {@code posts.grabber.action-button.enabled=true}.
     */
    private final ActionButton actionButton = new ActionButton();

    public boolean isWebsiteEnabled() {
        return websiteEnabled;
    }

    public void setWebsiteEnabled(boolean websiteEnabled) {
        this.websiteEnabled = websiteEnabled;
    }

    public Ai getAi() {
        return ai;
    }

    public ActionButton getActionButton() {
        return actionButton;
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
}

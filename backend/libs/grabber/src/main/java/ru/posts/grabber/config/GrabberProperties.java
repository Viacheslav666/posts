package ru.posts.grabber.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.grabber")
public class GrabberProperties {

    /** Feature flag for type=website (BE-17b). When false, API returns 404. */
    private boolean websiteEnabled = true;

    public boolean isWebsiteEnabled() {
        return websiteEnabled;
    }

    public void setWebsiteEnabled(boolean websiteEnabled) {
        this.websiteEnabled = websiteEnabled;
    }
}

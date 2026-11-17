package ru.posts.ratelimit.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.ratelimit")
public class PostsRateLimitProperties {

    private boolean enabled = true;
    private String redisUri = "redis://localhost:6379";
    private Map<String, Network> networks = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String redisUri() {
        return redisUri;
    }

    public void setRedisUri(String redisUri) {
        this.redisUri = redisUri;
    }

    public Map<String, Network> networks() {
        return networks;
    }

    public void setNetworks(Map<String, Network> networks) {
        this.networks = networks;
    }

    public static class Network {
        private long globalPerSecond = 5;
        private long tokenPerSecond = 5;
        private long chatPerMinute = 30;

        public long globalPerSecond() {
            return globalPerSecond;
        }

        public void setGlobalPerSecond(long globalPerSecond) {
            this.globalPerSecond = globalPerSecond;
        }

        public long tokenPerSecond() {
            return tokenPerSecond;
        }

        public void setTokenPerSecond(long tokenPerSecond) {
            this.tokenPerSecond = tokenPerSecond;
        }

        public long chatPerMinute() {
            return chatPerMinute;
        }

        public void setChatPerMinute(long chatPerMinute) {
            this.chatPerMinute = chatPerMinute;
        }
    }
}

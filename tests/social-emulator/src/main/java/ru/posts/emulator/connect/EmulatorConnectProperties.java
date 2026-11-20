package ru.posts.emulator.connect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.emulator")
public class EmulatorConnectProperties {

    /** Comma-separated browser app origins allowed as OAuth redirect_uri hosts. */
    private String allowedAppBaseUrls = "http://localhost:3000,http://127.0.0.1:3000";

    public List<String> allowedAppBaseUrls() {
        List<String> out = new ArrayList<>();
        for (String raw : allowedAppBaseUrls.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
        }
        return List.copyOf(out);
    }

    public String getAllowedAppBaseUrls() {
        return allowedAppBaseUrls;
    }

    public void setAllowedAppBaseUrls(String allowedAppBaseUrls) {
        this.allowedAppBaseUrls = allowedAppBaseUrls;
    }

    void setAllowedAppBaseUrlList(List<String> bases) {
        this.allowedAppBaseUrls = String.join(",", bases);
    }

    static EmulatorConnectProperties withBases(String... bases) {
        EmulatorConnectProperties props = new EmulatorConnectProperties();
        props.setAllowedAppBaseUrlList(Arrays.asList(bases));
        return props;
    }
}

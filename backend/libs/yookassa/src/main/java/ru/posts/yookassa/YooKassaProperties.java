package ru.posts.yookassa;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.billing.yookassa")
public class YooKassaProperties {

    /** Fake default for local/tests — never a real shop id (gitleaks-safe). */
    private String shopId = "test-shop-id-00000000";

    /** Fake default for local/tests — never a real secret (gitleaks-safe). */
    private String secretKey = "test-secret-key-not-a-real-credential";

    private String baseUrl = "https://api.yookassa.ru/v3";

    private String returnUrl = "http://localhost:3000/billing/return";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(15);

    /**
     * Official YooKassa webhook source ranges (IPv4 CIDR / host and IPv6 prefix).
     *
     * @see <a href="https://yookassa.ru/developers/using-api/webhooks">YooKassa webhooks</a>
     */
    private List<String> webhookIpCidrs = new ArrayList<>(List.of(
            "185.71.76.0/27",
            "185.71.77.0/27",
            "77.75.153.0/25",
            "77.75.156.11/32",
            "77.75.156.35/32",
            "77.75.154.128/25",
            "2a02:5180::/32"));

    private boolean receiptEnabled = true;

    private int vatCode = 1;

    public String shopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String secretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String returnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public List<String> webhookIpCidrs() {
        return webhookIpCidrs;
    }

    public void setWebhookIpCidrs(List<String> webhookIpCidrs) {
        this.webhookIpCidrs = webhookIpCidrs;
    }

    public boolean receiptEnabled() {
        return receiptEnabled;
    }

    public void setReceiptEnabled(boolean receiptEnabled) {
        this.receiptEnabled = receiptEnabled;
    }

    public int vatCode() {
        return vatCode;
    }

    public void setVatCode(int vatCode) {
        this.vatCode = vatCode;
    }
}

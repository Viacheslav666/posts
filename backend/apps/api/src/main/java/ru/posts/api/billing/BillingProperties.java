package ru.posts.api.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.billing")
public class BillingProperties {

    /** Global default model: balance | subscription. Per-workspace override in workspaces.billing_model. */
    private String model = "balance";

    private int graceDays = 3;

    private int trialDays = 7;

    /** Registration bonus in kopecks; 0 = disabled (default). */
    private long registrationBonusKopecks = 0L;

    private long autopayDefaultThresholdKopecks = 10_000L;

    private long autopayDefaultTopupKopecks = 50_000L;

    private long autopayMinTopupKopecks = 10_000L;

    private int dailyChargeBatchSize = 500;

    private int lowBalanceDaysThreshold = 3;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isBalanceModel() {
        return !"subscription".equalsIgnoreCase(model);
    }

    public int getGraceDays() {
        return graceDays;
    }

    public void setGraceDays(int graceDays) {
        this.graceDays = graceDays;
    }

    public int getTrialDays() {
        return trialDays;
    }

    public void setTrialDays(int trialDays) {
        this.trialDays = trialDays;
    }

    public long getRegistrationBonusKopecks() {
        return registrationBonusKopecks;
    }

    public void setRegistrationBonusKopecks(long registrationBonusKopecks) {
        this.registrationBonusKopecks = registrationBonusKopecks;
    }

    public long getAutopayDefaultThresholdKopecks() {
        return autopayDefaultThresholdKopecks;
    }

    public void setAutopayDefaultThresholdKopecks(long autopayDefaultThresholdKopecks) {
        this.autopayDefaultThresholdKopecks = autopayDefaultThresholdKopecks;
    }

    public long getAutopayDefaultTopupKopecks() {
        return autopayDefaultTopupKopecks;
    }

    public void setAutopayDefaultTopupKopecks(long autopayDefaultTopupKopecks) {
        this.autopayDefaultTopupKopecks = autopayDefaultTopupKopecks;
    }

    public long getAutopayMinTopupKopecks() {
        return autopayMinTopupKopecks;
    }

    public void setAutopayMinTopupKopecks(long autopayMinTopupKopecks) {
        this.autopayMinTopupKopecks = autopayMinTopupKopecks;
    }

    public int getDailyChargeBatchSize() {
        return dailyChargeBatchSize;
    }

    public void setDailyChargeBatchSize(int dailyChargeBatchSize) {
        this.dailyChargeBatchSize = dailyChargeBatchSize;
    }

    public int getLowBalanceDaysThreshold() {
        return lowBalanceDaysThreshold;
    }

    public void setLowBalanceDaysThreshold(int lowBalanceDaysThreshold) {
        this.lowBalanceDaysThreshold = lowBalanceDaysThreshold;
    }
}

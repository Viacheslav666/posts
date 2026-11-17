package ru.posts.emulator.control;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkScenario {

    @Min(0)
    private long delayMs;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double errorRate;

    /** Forced fault: 6,9,10,15,100,5xx,429,403/permanent,drop,hang (null = none). */
    private String forcedError;

    @Min(0)
    private long hangMs = 30_000;

    @Min(0)
    private int retryAfterSeconds = 5;

    private Integer httpStatus;

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public String getForcedError() {
        return forcedError;
    }

    public void setForcedError(String forcedError) {
        this.forcedError = forcedError;
    }

    public long getHangMs() {
        return hangMs;
    }

    public void setHangMs(long hangMs) {
        this.hangMs = hangMs;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(int retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public NetworkScenario copy() {
        NetworkScenario copy = new NetworkScenario();
        copy.delayMs = delayMs;
        copy.errorRate = errorRate;
        copy.forcedError = forcedError;
        copy.hangMs = hangMs;
        copy.retryAfterSeconds = retryAfterSeconds;
        copy.httpStatus = httpStatus;
        return copy;
    }
}

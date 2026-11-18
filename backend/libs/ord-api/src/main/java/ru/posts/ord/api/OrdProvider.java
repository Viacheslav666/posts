package ru.posts.ord.api;

/**
 * Port for a single ORD (ad-marking) provider. Apps collect {@code List<OrdProvider>} and route by
 * {@link #providerId()}.
 */
public interface OrdProvider {

    OrdProviderId providerId();

    /** Verifies the API key can reach the provider (lightweight health call). */
    void testConnection(OrdCredentials credentials);

    void upsertPerson(OrdCredentials credentials, OrdPersonRequest person);

    void upsertContract(OrdCredentials credentials, OrdContractRequest contract);

    CreativeRegistrationResult registerCreative(OrdCredentials credentials, OrdCreativeRequest creative);

    /** Registers / updates an advertising pad (platform) used in statistics. Idempotent by external id. */
    default void upsertPlatform(OrdCredentials credentials, OrdPlatformRequest platform) {
        // Optional for providers that accept pad ids without prior registration.
    }

    void submitInvoice(OrdCredentials credentials, OrdInvoiceRequest invoice);

    void submitStatistics(OrdCredentials credentials, OrdStatisticsRequest statistics);

    /** Soft-deletes an invoice/act in the ORD provider (VK ORD DELETE invoice). */
    default void deleteInvoice(OrdCredentials credentials, String invoiceExternalId) {
        throw new OrdApiException(
                OrdErrorClass.PERMANENT,
                "deleteInvoice is not supported for " + providerId().code());
    }

    /** Checks ERID / creative status in the ORD provider. */
    default EridCheckResult checkErid(OrdCredentials credentials, String erid) {
        throw new OrdApiException(
                OrdErrorClass.PERMANENT,
                "checkErid is not supported for " + providerId().code());
    }

    OrdErrorClass classifyError(Throwable error);
}

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

    void submitInvoice(OrdCredentials credentials, OrdInvoiceRequest invoice);

    void submitStatistics(OrdCredentials credentials, OrdStatisticsRequest statistics);

    OrdErrorClass classifyError(Throwable error);
}

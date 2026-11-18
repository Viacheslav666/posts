package ru.posts.ord.yandex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.ord.api.CreativeRegistrationResult;
import ru.posts.ord.api.OrdApiException;
import ru.posts.ord.api.OrdContractRequest;
import ru.posts.ord.api.OrdCreativeRequest;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.api.OrdErrorClass;
import ru.posts.ord.api.OrdInvoiceRequest;
import ru.posts.ord.api.OrdPersonRequest;
import ru.posts.ord.api.OrdProvider;
import ru.posts.ord.api.OrdProviderId;
import ru.posts.ord.api.OrdStatisticsItem;
import ru.posts.ord.api.OrdStatisticsRequest;

/**
 * Yandex ORD HTTP adapter (API v8).
 *
 * <p>Public docs:
 *
 * <ul>
 *   <li><a href="https://yandex.ru/support/ord/ru/api/connect">Connect / auth</a>
 *   <li>Spec: <a href="https://ord-prestable.yandex.net/api/docs">prestable</a> / <a
 *       href="https://ord.yandex.net/api/docs">prod</a>
 *   <li>Auth: {@code Authorization: Bearer {token}}
 *   <li>{@code POST /api/v8/organization}, {@code /contract}, {@code /creative} (response {@code
 *       token} = erid), {@code /invoice}, {@code /statistics}, {@code /platforms}
 * </ul>
 */
public class YandexOrdProvider implements OrdProvider {

    private static final String HEALTH_PROBE_ID = "ping-health";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public YandexOrdProvider(OrdYandexProperties properties, ObjectMapper objectMapper) {
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://ord-prestable.yandex.net");
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public OrdProviderId providerId() {
        return OrdProviderId.YANDEX_ORD;
    }

    /**
     * Probe reachability via {@code GET /api/v8/organization?object_id=ping-health}. 404/422 mean
     * the API is reachable with a valid key; 401 means bad credentials.
     */
    @Override
    public void testConnection(OrdCredentials credentials) {
        try {
            restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v8/organization")
                            .queryParam("object_id", HEALTH_PROBE_ID)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 422) {
                return;
            }
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "Yandex ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(
                    OrdErrorClass.RETRYABLE, "Yandex ORD transport error: " + ex.getMessage(), null, ex);
        }
    }

    @Override
    public void upsertPerson(OrdCredentials credentials, OrdPersonRequest person) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "id", person.externalId());
        putText(body, "type", mapOrgType(person.orgType()));
        putText(body, "name", person.name());
        putText(body, "inn", person.inn());
        putText(body, "kpp", person.kpp());
        putText(body, "mobilePhone", person.phone());
        putText(body, "oksmNumber", person.oksmCountry());
        body.put("isOrs", hasRole(person.roles(), "ors"));
        body.put("isRr", hasRole(person.roles(), "publisher", "rr"));
        Map<String, String> foreign = person.foreignFields();
        putText(body, "alternativeInn", firstNonBlank(foreign.get("foreign_inn"), foreign.get("alternativeInn")));
        putText(body, "regNumber", firstNonBlank(foreign.get("foreign_reg_number"), foreign.get("regNumber")));
        putText(body, "epayNumber", firstNonBlank(foreign.get("foreign_epayment"), foreign.get("epayNumber")));
        if ((person.oksmCountry() == null || person.oksmCountry().isBlank())
                && foreign.containsKey("foreign_oksm_country_code")) {
            putText(body, "oksmNumber", foreign.get("foreign_oksm_country_code"));
        }
        postJson(credentials, "/api/v8/organization", body);
    }

    @Override
    public void upsertContract(OrdCredentials credentials, OrdContractRequest contract) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "id", contract.externalId());
        putText(body, "type", mapContractType(contract.type()));
        putText(body, "clientId", contract.clientExternalId());
        putText(body, "contractorId", contract.contractorExternalId());
        putText(body, "number", contract.serial());
        if (contract.dateStart() != null) {
            body.put("date", contract.dateStart().toString());
        }
        if (contract.amount() != null) {
            body.put("amount", contract.amount().toPlainString());
        }
        body.put("isRegReport", true);
        putText(body, "subjectType", mapSubjectType(contract.subjectType()));
        putText(body, "actionType", contract.actionType());
        putText(body, "contractId", contract.parentContractExternalId());
        postJson(credentials, "/api/v8/contract", body);
    }

    @Override
    public CreativeRegistrationResult registerCreative(OrdCredentials credentials, OrdCreativeRequest creative) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "id", creative.externalId());
        body.put("creativeType", "creative");
        putText(body, "form", normalizeForm(creative.form()));
        putText(body, "description", firstNonBlank(creative.description(), creative.name()));
        body.put("isSocial", false);
        body.put("isSocialQuota", false);
        body.put("coBranding", creative.contractExternalIds().size() > 1);
        if (creative.selfPromo()) {
            putText(body, "selfPromotionOrganizationId", creative.selfPromoOrgExternalId());
        } else if (!creative.contractExternalIds().isEmpty()) {
            ArrayNode contracts = body.putArray("contractIds");
            creative.contractExternalIds().forEach(contracts::add);
        }
        if (!creative.kktuCodes().isEmpty()) {
            ArrayNode kktu = body.putArray("kktuCodes");
            creative.kktuCodes().forEach(kktu::add);
        }
        if (!creative.texts().isEmpty()) {
            ArrayNode textData = body.putArray("textData");
            creative.texts().forEach(textData::add);
        } else if (creative.name() != null && !creative.name().isBlank()) {
            body.putArray("textData").add(creative.name());
        }
        if (!creative.targetUrls().isEmpty()) {
            ArrayNode urls = body.putArray("urls");
            creative.targetUrls().forEach(urls::add);
        }
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/api/v8/creative")
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String erid = textOrNull(response, "token");
            if (erid == null || erid.isBlank()) {
                erid = firstObjectToken(response);
            }
            if (erid == null || erid.isBlank()) {
                throw new OrdApiException(OrdErrorClass.RETRYABLE, "Yandex ORD creative missing token/erid");
            }
            String requestId = textOrNull(response, "request_id");
            String providerRef = requestId != null ? requestId : erid;
            return new CreativeRegistrationResult(creative.externalId(), erid, providerRef);
        } catch (OrdApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "Yandex ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(
                    OrdErrorClass.RETRYABLE, "Yandex ORD transport error: " + ex.getMessage(), null, ex);
        }
    }

    @Override
    public void submitInvoice(OrdCredentials credentials, OrdInvoiceRequest invoice) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "id", invoice.externalId());
        putText(body, "contractId", invoice.contractExternalId());
        putText(body, "number", invoice.serial());
        putText(body, "clientRole", invoice.clientRole());
        putText(body, "contractorRole", invoice.contractorRole());
        if (invoice.date() != null) {
            body.put("date", invoice.date().toString());
        }
        if (invoice.dateStart() != null) {
            body.put("startDate", invoice.dateStart().toString());
        }
        if (invoice.dateEnd() != null) {
            body.put("endDate", invoice.dateEnd().toString());
        }
        if (invoice.amount() != null) {
            ObjectNode amount = body.putObject("amount");
            amount.put("includingVat", invoice.amount().toPlainString());
            putText(amount, "vatRate", invoice.vatRate());
        }
        if (!invoice.items().isEmpty()) {
            body.set("items", objectMapper.valueToTree(invoice.items()));
        }
        postJson(credentials, "/api/v8/invoice", body);
    }

    @Override
    public void submitStatistics(OrdCredentials credentials, OrdStatisticsRequest statistics) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode rows = body.putArray("statistics");
        for (OrdStatisticsItem item : statistics.items()) {
            ObjectNode row = rows.addObject();
            putText(row, "creativeId", item.creativeExternalId());
            putText(row, "platformId", item.platformExternalId());
            row.put("impsFact", item.showsCount());
            if (item.dateStartActual() != null) {
                row.put("dateStartFact", item.dateStartActual().toString());
            }
            if (item.dateEndActual() != null) {
                row.put("dateEndFact", item.dateEndActual().toString());
            }
            if (item.dateStartPlanned() != null) {
                row.put("dateStartPlan", item.dateStartPlanned().toString());
            }
            if (item.dateEndPlanned() != null) {
                row.put("dateEndPlan", item.dateEndPlanned().toString());
            }
            putText(row, "invoiceId", item.invoiceExternalId());
            putText(row, "contractId", item.contractExternalId());
            putText(row, "type", item.payType());
            if (item.amount() != null) {
                ObjectNode amount = row.putObject("amount");
                amount.put("includingVat", item.amount().toPlainString());
            }
        }
        postJson(credentials, "/api/v8/statistics", body);
    }

    @Override
    public OrdErrorClass classifyError(Throwable error) {
        if (error instanceof OrdApiException ord) {
            return ord.errorClass();
        }
        if (error instanceof RestClientResponseException http) {
            return classifyHttpStatus(http.getStatusCode().value());
        }
        if (error instanceof ResourceAccessException || error instanceof RestClientException) {
            return OrdErrorClass.RETRYABLE;
        }
        return OrdErrorClass.RETRYABLE;
    }

    private void postJson(OrdCredentials credentials, String path, Object body) {
        try {
            restClient
                    .post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "Yandex ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(
                    OrdErrorClass.RETRYABLE, "Yandex ORD transport error: " + ex.getMessage(), null, ex);
        }
    }

    private OrdApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return new OrdApiException(classifyHttpStatus(status), "Yandex ORD HTTP " + status, status, ex);
    }

    private static OrdErrorClass classifyHttpStatus(int status) {
        if (status == 429) {
            return OrdErrorClass.RATE_LIMITED;
        }
        if (status == 401 || status == 403) {
            return OrdErrorClass.AUTH;
        }
        if (status >= 500) {
            return OrdErrorClass.RETRYABLE;
        }
        if (status == 400 || status == 422) {
            return OrdErrorClass.VALIDATION;
        }
        if (status >= 400) {
            return OrdErrorClass.PERMANENT;
        }
        return OrdErrorClass.RETRYABLE;
    }

    private static String mapOrgType(String orgType) {
        if (orgType == null || orgType.isBlank()) {
            return "ul";
        }
        return switch (orgType.toLowerCase(Locale.ROOT)) {
            case "physical", "fl" -> "fl";
            case "ip" -> "ip";
            case "juridical", "ul" -> "ul";
            case "foreign_physical", "ffl" -> "ffl";
            case "foreign_juridical", "ful" -> "ful";
            default -> orgType;
        };
    }

    private static String mapContractType(String type) {
        if (type == null || type.isBlank()) {
            return "contract";
        }
        return switch (type) {
            case "service", "contract" -> "contract";
            case "mediation", "intermediary-contract" -> "intermediary-contract";
            case "additional", "additional-agreement" -> "additional-agreement";
            default -> type;
        };
    }

    private static String mapSubjectType(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return null;
        }
        return switch (subjectType) {
            case "org_distribution", "org-distribution" -> "org-distribution";
            case "mediation" -> "mediation";
            case "distribution" -> "distribution";
            default -> subjectType;
        };
    }

    private static boolean hasRole(List<String> roles, String... needles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            String normalized = role.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (normalized.equals(needle) || normalized.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String firstObjectToken(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode statuses = response.get("object_statuses");
        if (statuses != null && statuses.isArray() && !statuses.isEmpty()) {
            return textOrNull(statuses.get(0), "token");
        }
        return null;
    }

    private static String normalizeForm(String form) {
        if (form == null || form.isBlank()) {
            return "text-block";
        }
        // Accept VK-style underscores from shared OrdCreativeRequest payloads.
        return form.replace('_', '-');
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void putText(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String bearer(OrdCredentials credentials) {
        return "Bearer " + credentials.apiKey();
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

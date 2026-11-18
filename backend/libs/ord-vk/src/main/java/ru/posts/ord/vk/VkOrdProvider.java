package ru.posts.ord.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
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
 * VK ORD HTTP adapter.
 *
 * <p>Public docs:
 *
 * <ul>
 *   <li><a href="https://ord.vk.ru/help/api/api">API overview</a> — base URLs {@code
 *       https://api.ord.vk.com} / sandbox {@code https://api-sandbox.ord.vk.com}, auth {@code
 *       Authorization: Bearer {apiKey}}
 *   <li>Person: {@code PUT/GET /v1/person/{external_id}}
 *   <li>Contract: {@code PUT/GET /v1/contract/{external_id}}
 *   <li>Creative v3: {@code PUT /v3/creative/{external_id}} → {@code {erid, marker}}
 *   <li>Invoice: {@code PUT /v1/invoice/{external_id}}
 *   <li>Statistics: {@code POST /v1/statistics}
 *   <li>Health: {@code GET /v1/person/my}
 * </ul>
 */
public class VkOrdProvider implements OrdProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VkOrdProvider(OrdVkProperties properties, ObjectMapper objectMapper) {
        String baseUrl = trimTrailingSlash(properties.baseUrl(), "https://api-sandbox.ord.vk.com");
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
        return OrdProviderId.VK_ORD;
    }

    @Override
    public void testConnection(OrdCredentials credentials) {
        try {
            restClient
                    .get()
                    .uri("/v1/person/my")
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD transport error: " + ex.getMessage(), null, ex);
        }
    }

    @Override
    public void upsertPerson(OrdCredentials credentials, OrdPersonRequest person) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "name", person.name());
        if (person.roles() != null && !person.roles().isEmpty()) {
            ArrayNode roles = body.putArray("roles");
            person.roles().forEach(roles::add);
        }
        ObjectNode juridical = body.putObject("juridical_details");
        putText(juridical, "type", person.orgType());
        putText(juridical, "inn", person.inn());
        putText(juridical, "phone", person.phone());
        if (person.kpp() != null && !person.kpp().isBlank()) {
            putText(juridical, "kpp", person.kpp());
        }
        Map<String, String> foreign = person.foreignFields();
        putText(juridical, "foreign_inn", foreign.get("foreign_inn"));
        putText(juridical, "foreign_registration_number", foreign.get("foreign_reg_number"));
        putText(juridical, "foreign_epayment_method", foreign.get("foreign_epayment"));
        String oksm = person.oksmCountry();
        if (oksm == null || oksm.isBlank()) {
            oksm = foreign.get("foreign_oksm_country_code");
        }
        putText(juridical, "foreign_oksm_country_code", oksm);
        putJson(credentials, "/v1/person/" + person.externalId(), body);
    }

    @Override
    public void upsertContract(OrdCredentials credentials, OrdContractRequest contract) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "type", mapContractType(contract.type()));
        putText(body, "client_external_id", contract.clientExternalId());
        putText(body, "contractor_external_id", contract.contractorExternalId());
        if (contract.dateStart() != null) {
            body.put("date", contract.dateStart().toString());
        }
        putText(body, "serial", contract.serial());
        putText(body, "subject_type", contract.subjectType());
        putText(body, "action_type", contract.actionType());
        putText(body, "parent_contract_external_id", contract.parentContractExternalId());
        if (contract.amount() != null) {
            body.put("amount", contract.amount().toPlainString());
        }
        ArrayNode flags = body.putArray("flags");
        for (String flag : contract.flags()) {
            flags.add(flag);
        }
        if (contract.vatRate() != null
                && !contract.vatRate().isBlank()
                && !"without_vat".equalsIgnoreCase(contract.vatRate())
                && !containsFlag(contract.flags(), "vat_included")) {
            flags.add("vat_included");
        }
        putJson(credentials, "/v1/contract/" + contract.externalId(), body);
    }

    @Override
    public CreativeRegistrationResult registerCreative(OrdCredentials credentials, OrdCreativeRequest creative) {
        ObjectNode body = objectMapper.createObjectNode();
        if (creative.selfPromo()) {
            putText(body, "person_external_id", creative.selfPromoOrgExternalId());
        } else if (!creative.contractExternalIds().isEmpty()) {
            ArrayNode contracts = body.putArray("contract_external_ids");
            creative.contractExternalIds().forEach(contracts::add);
        }
        putText(body, "name", creative.name());
        putText(body, "description", creative.description());
        putText(body, "brand", creative.brand());
        putText(body, "category", creative.category());
        putText(body, "form", creative.form() == null || creative.form().isBlank() ? "text_block" : creative.form());
        putText(
                body,
                "pay_type",
                creative.payType() == null || creative.payType().isBlank() ? "other" : creative.payType());
        if (!creative.kktuCodes().isEmpty()) {
            ArrayNode kktus = body.putArray("kktus");
            creative.kktuCodes().forEach(kktus::add);
        }
        if (!creative.texts().isEmpty()) {
            ArrayNode texts = body.putArray("texts");
            creative.texts().forEach(texts::add);
        }
        if (!creative.targetUrls().isEmpty()) {
            ArrayNode urls = body.putArray("target_urls");
            creative.targetUrls().forEach(urls::add);
        }
        if (!creative.mediaExternalIds().isEmpty()) {
            ArrayNode media = body.putArray("media_external_ids");
            creative.mediaExternalIds().forEach(media::add);
        }
        try {
            JsonNode response = restClient
                    .put()
                    .uri("/v3/creative/{externalId}", creative.externalId())
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String erid = textOrNull(response, "erid");
            if (erid == null || erid.isBlank()) {
                throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD creative missing erid");
            }
            String marker = textOrNull(response, "marker");
            String providerRef = marker != null ? marker : erid;
            return new CreativeRegistrationResult(creative.externalId(), erid, providerRef);
        } catch (OrdApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD transport error: " + ex.getMessage(), null, ex);
        }
    }

    @Override
    public void submitInvoice(OrdCredentials credentials, OrdInvoiceRequest invoice) {
        ObjectNode body = objectMapper.createObjectNode();
        putText(body, "contract_external_id", invoice.contractExternalId());
        if (invoice.date() != null) {
            body.put("date", invoice.date().toString());
        }
        if (invoice.dateStart() != null) {
            body.put("date_start", invoice.dateStart().toString());
        }
        if (invoice.dateEnd() != null) {
            body.put("date_end", invoice.dateEnd().toString());
        }
        putText(body, "serial", invoice.serial());
        if (invoice.amount() != null) {
            body.put("amount", invoice.amount().toPlainString());
        }
        putText(body, "vat_rate", invoice.vatRate());
        putText(body, "client_role", invoice.clientRole());
        putText(body, "contractor_role", invoice.contractorRole());
        if (!invoice.items().isEmpty()) {
            body.set("items", objectMapper.valueToTree(invoice.items()));
        }
        putJson(credentials, "/v1/invoice/" + invoice.externalId(), body);
    }

    @Override
    public void submitStatistics(OrdCredentials credentials, OrdStatisticsRequest statistics) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode items = body.putArray("items");
        for (OrdStatisticsItem item : statistics.items()) {
            ObjectNode row = items.addObject();
            putText(row, "creative_external_id", item.creativeExternalId());
            putText(row, "pad_external_id", item.platformExternalId());
            row.put("shows_count", item.showsCount());
            if (item.dateStartActual() != null) {
                row.put("date_start_actual", item.dateStartActual().toString());
            }
            if (item.dateEndActual() != null) {
                row.put("date_end_actual", item.dateEndActual().toString());
            }
            if (item.dateStartPlanned() != null) {
                row.put("date_start_planned", item.dateStartPlanned().toString());
            }
            if (item.dateEndPlanned() != null) {
                row.put("date_end_planned", item.dateEndPlanned().toString());
            }
            if (item.amount() != null) {
                row.put("amount", item.amount().toPlainString());
            }
            putText(row, "pay_type", item.payType());
        }
        try {
            restClient
                    .post()
                    .uri("/v1/statistics")
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD transport error: " + ex.getMessage(), null, ex);
        }
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

    private void putJson(OrdCredentials credentials, String path, Object body) {
        try {
            restClient
                    .put()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer(credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD timeout: " + ex.getMessage(), null, ex);
        } catch (RestClientException ex) {
            throw new OrdApiException(OrdErrorClass.RETRYABLE, "VK ORD transport error: " + ex.getMessage(), null, ex);
        }
    }

    private OrdApiException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        OrdErrorClass clazz = classifyHttpStatus(status);
        return new OrdApiException(clazz, "VK ORD HTTP " + status, status, ex);
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

    private static String mapContractType(String type) {
        if (type == null || type.isBlank()) {
            return "service";
        }
        return switch (type) {
            case "mediation", "intermediary-contract" -> "mediation";
            case "additional", "additional-agreement" -> "additional";
            case "service", "contract" -> "service";
            default -> type;
        };
    }

    private static boolean containsFlag(List<String> flags, String needle) {
        for (String flag : flags) {
            if (needle.equals(flag)) {
                return true;
            }
        }
        return false;
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

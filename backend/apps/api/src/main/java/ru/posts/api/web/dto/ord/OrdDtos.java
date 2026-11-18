package ru.posts.api.web.dto.ord;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** ORD / ad-marking API DTOs (BE-15). */
public final class OrdDtos {

    private OrdDtos() {}

    @Schema(name = "OrdProviderCode", description = "ORD provider identifier")
    public enum OrdProviderCode {
        vk_ord,
        yandex_ord
    }

    @Schema(name = "OrdConnection", description = "ORD connection without plaintext API key")
    public record OrdConnection(
            UUID id,
            UUID workspaceId,
            OrdProviderCode provider,
            String publisherInn,
            String publisherName,
            String orgType,
            String phone,
            List<String> roles,
            String status,
            boolean isDefault,
            boolean hasApiKey,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "UpsertOrdConnectionRequest")
    public record UpsertOrdConnectionRequest(
            @Schema(description = "Plaintext API key; omit/blank on update to keep existing") String apiKey,
            String publisherInn,
            String publisherName,
            String orgType,
            String phone,
            List<String> roles,
            Boolean isDefault,
            @NotNull Boolean responsibilityAccepted) {}

    @Schema(name = "OrdConnectionTestResponse")
    public record OrdConnectionTestResponse(boolean ok, String status, String message) {}

    @Schema(name = "OrdContractor")
    public record OrdContractor(
            UUID id,
            UUID workspaceId,
            String inn,
            String kpp,
            String name,
            String orgType,
            String countryOksm,
            String phone,
            String address,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CreateOrdContractorRequest")
    public record CreateOrdContractorRequest(
            @NotBlank String inn,
            String kpp,
            @NotBlank String name,
            @NotBlank String orgType,
            String countryOksm,
            String phone,
            String address) {}

    @Schema(name = "UpdateOrdContractorRequest")
    public record UpdateOrdContractorRequest(
            String inn, String kpp, String name, String orgType, String countryOksm, String phone, String address) {}

    @Schema(name = "OrdContract")
    public record OrdContract(
            UUID id,
            UUID workspaceId,
            UUID contractorId,
            String externalId,
            String serial,
            LocalDate dateStart,
            LocalDate dateEnd,
            BigDecimal amount,
            String vatRate,
            OrdProviderCode provider,
            boolean selfPromo,
            String status,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CreateOrdContractRequest")
    public record CreateOrdContractRequest(
            @NotNull UUID contractorId,
            @NotBlank String externalId,
            String serial,
            LocalDate dateStart,
            LocalDate dateEnd,
            BigDecimal amount,
            String vatRate,
            @NotNull OrdProviderCode provider,
            Boolean selfPromo) {}

    @Schema(name = "OrdCreativeStatus")
    public record OrdCreativeStatus(
            UUID id,
            UUID postId,
            String externalId,
            String erid,
            OrdProviderCode provider,
            String status,
            String lastError,
            Instant registeredAt) {}

    @Schema(name = "OrdRegisterCreativeResponse")
    public record OrdRegisterCreativeResponse(
            UUID id,
            UUID postId,
            String externalId,
            String erid,
            OrdProviderCode provider,
            String status,
            String lastError,
            Instant registeredAt) {}

    @Schema(name = "DictionaryKktuItem")
    public record DictionaryKktuItem(String code, String name, String parentCode, boolean active) {}

    @Schema(name = "DictionaryOksmItem")
    public record DictionaryOksmItem(String code, String name, String alpha2, boolean active) {}

    @Schema(name = "OrdReportRunRequest")
    public record OrdReportRunRequest(
            @NotNull UUID workspaceId,
            OrdProviderCode provider,
            @NotNull @Min(2020) @Max(2100) Integer year,
            @NotNull @Min(1) @Max(12) Integer month) {}

    @Schema(name = "OrdReportRunResponse")
    public record OrdReportRunResponse(
            UUID id, String status, int invoicesCount, int statisticsCount, int periodYear, int periodMonth) {}

    @Schema(
            name = "AdMarking",
            description = "Full advertiser / ORD marking block (feature spec §3.4). Stored as posts.ad_payload jsonb.")
    public record AdMarking(
            @Schema(description = "Advertiser name / FIO") String advertiserName,
            @Schema(description = "INN — 10 or 12 digits") String inn,
            @Schema(description = "KPP; required for juridical") String kpp,
            @Schema(
                            description = "physical | ip | juridical | foreign_physical | foreign_juridical",
                            allowableValues = {"physical", "ip", "juridical", "foreign_physical", "foreign_juridical"})
                    String orgType,
            @DecimalMin("0") BigDecimal amount,
            @DecimalMin("0") BigDecimal contractAmount,
            LocalDate contractDate,
            LocalDate contractDateEnd,
            String contractSerial,
            @Size(min = 1) List<String> kktuCodes,
            @Schema(description = "OKSM country code", example = "643") String oksmCountry,
            @Schema(description = "VAT rate", example = "without_vat") String vatRate,
            List<String> roles,
            Boolean isForeign,
            String foreignInn,
            String foreignRegNumber,
            String foreignEpayment,
            String phone,
            String address,
            @Size(max = 1000) String description,
            @Schema(description = "Marking mode / type_marking", example = "0") String markingType,
            Boolean selfPromo,
            @Min(1) @Max(365) Integer displayPeriodDays,
            Boolean autoDeleteAfterPeriod,
            UUID contractorId,
            @Schema(description = "Erid after registration; read-only on responses", nullable = true) String erid) {}
}

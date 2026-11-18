package ru.posts.api.ord;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.ord.api.MarkingPlacement;
import ru.posts.ord.api.VatRate;

/** Validates ad marking payload and network-specific post options (BE-15 / BE-25). */
public final class AdMarkingValidator {

    private static final Set<String> ORG_TYPES =
            Set.of("physical", "ip", "juridical", "foreign_physical", "foreign_juridical");
    private static final Set<String> CUSTOMER_ROLES = Set.of("advertiser", "agency", "publisher", "ors");

    private AdMarkingValidator() {}

    public static void requireValidWhenAd(boolean ad, OrdDtos.AdMarking marking) {
        requireValidWhenAd(ad, marking, null, null);
    }

    /**
     * Validates ad marking, optionally against dictionary membership.
     *
     * @param knownKktu active KKTU codes; when null, dictionary membership is not checked (unit tests)
     * @param knownOksm active OKSM codes; when null, membership is not checked
     */
    public static void requireValidWhenAd(
            boolean ad, OrdDtos.AdMarking marking, Set<String> knownKktu, Set<String> knownOksm) {
        if (!ad) {
            return;
        }
        if (marking == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "Ad marking is required when ad=true");
        }
        if (marking.advertiserName() == null || marking.advertiserName().isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "advertiserName is required");
        }
        boolean foreign = Boolean.TRUE.equals(marking.isForeign())
                || (marking.orgType() != null && marking.orgType().startsWith("foreign_"));
        if (!InnValidator.isValid(marking.inn(), foreign)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.AD_INN_INVALID,
                    foreign ? "Foreign tax id is required" : "INN must be 10 or 12 digits with valid checksum");
        }
        if (marking.orgType() != null && !marking.orgType().isBlank() && !ORG_TYPES.contains(marking.orgType())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "Unsupported orgType");
        }
        if ("juridical".equals(marking.orgType())
                && (marking.kpp() == null || marking.kpp().isBlank())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "KPP is required for juridical");
        }
        if ("physical".equals(marking.orgType()) || "ip".equals(marking.orgType())) {
            String inn = marking.inn() == null ? "" : marking.inn().trim();
            if (!foreign && inn.length() != 12) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.AD_INN_INVALID,
                        "physical/IP require 12-digit INN");
            }
        }
        if ("juridical".equals(marking.orgType()) && !foreign) {
            String inn = marking.inn() == null ? "" : marking.inn().trim();
            if (inn.length() != 10) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.AD_INN_INVALID,
                        "juridical requires 10-digit INN");
            }
        }
        if (marking.kktuCodes() == null || marking.kktuCodes().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "At least one KKTU code is required");
        }
        if (knownKktu != null) {
            for (String code : marking.kktuCodes()) {
                if (code == null || !knownKktu.contains(code.trim())) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ProblemCodes.ORD_VALIDATION_FAILED,
                            "Unknown KKTU code: " + code);
                }
            }
        }
        if (foreign) {
            if (marking.oksmCountry() == null || marking.oksmCountry().isBlank()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.ORD_VALIDATION_FAILED,
                        "oksmCountry is required for foreign counterparties");
            }
            if (knownOksm != null && !knownOksm.contains(marking.oksmCountry().trim())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.ORD_VALIDATION_FAILED,
                        "Unknown OKSM country: " + marking.oksmCountry());
            }
            if ((marking.foreignInn() == null || marking.foreignInn().isBlank())
                    && (marking.foreignRegNumber() == null
                            || marking.foreignRegNumber().isBlank())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.ORD_VALIDATION_FAILED,
                        "foreignInn or foreignRegNumber is required for foreign counterparties");
            }
        } else if (marking.oksmCountry() != null
                && !marking.oksmCountry().isBlank()
                && knownOksm != null
                && !knownOksm.contains(marking.oksmCountry().trim())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "Unknown OKSM country: " + marking.oksmCountry());
        }
        if (marking.vatRate() != null && !marking.vatRate().isBlank() && !VatRate.isSupported(marking.vatRate())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "Unsupported vatRate");
        }
        boolean selfPromo = Boolean.TRUE.equals(marking.selfPromo());
        if (!selfPromo && marking.contractDate() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "contractDate is required unless selfPromo");
        }
        if (marking.contractDate() != null
                && marking.contractDateEnd() != null
                && marking.contractDateEnd().isBefore(marking.contractDate())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "contractDateEnd must be on or after contractDate");
        }
        if (Boolean.TRUE.equals(marking.socialAdQuota()) && !Boolean.TRUE.equals(marking.socialAd())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "socialAdQuota requires socialAd=true");
        }
        if (marking.customerRole() != null
                && !marking.customerRole().isBlank()
                && !CUSTOMER_ROLES.contains(marking.customerRole().trim().toLowerCase(Locale.ROOT))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "Unsupported customerRole");
        }
        if (marking.executor() != null && !marking.executor().isBlank()) {
            String exec = marking.executor().trim();
            if (!"self".equalsIgnoreCase(exec)) {
                try {
                    java.util.UUID.fromString(exec);
                } catch (IllegalArgumentException ex) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ProblemCodes.ORD_VALIDATION_FAILED,
                            "executor must be 'self' or a contractor UUID");
                }
            }
        }
        // Validate placement / markingType resolve without error.
        try {
            String placementName =
                    marking.placement() == null ? null : marking.placement().name();
            MarkingPlacement.resolve(placementName, marking.markingType());
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "Invalid marking placement");
        }
    }

    public static Set<String> toSet(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(codes);
    }

    public static void validateNetworkOptions(ApiEnums.Network network, PostDtos.PostVariantOptions options) {
        if (options == null || network == null) {
            return;
        }
        if (options.donutDays() != null && network != ApiEnums.Network.vk) {
            throw unsupported("donutDays", network);
        }
        if (options.donutDays() != null && (options.donutDays() < 0 || options.donutDays() > 7)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.VALIDATION_FAILED,
                    "donutDays must be between 0 and 7");
        }
        if (options.coOwnerIds() != null && !options.coOwnerIds().isEmpty() && network != ApiEnums.Network.vk) {
            throw unsupported("coOwnerIds", network);
        }
        if (options.coOwnerIds() != null && options.coOwnerIds().size() > 4) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, "coOwnerIds max size is 4");
        }
        if (options.copyrightUrl() != null && !options.copyrightUrl().isBlank() && network != ApiEnums.Network.vk) {
            throw unsupported("copyrightUrl", network);
        }
        if (options.actionButton() != null && !options.actionButton().isEmpty() && network != ApiEnums.Network.vk) {
            throw unsupported("actionButton", network);
        }
        if (options.primaryAttachmentsMode() != null
                && !options.primaryAttachmentsMode().isBlank()
                && network != ApiEnums.Network.vk) {
            throw unsupported("primaryAttachmentsMode", network);
        }
        if (options.geo() != null && network != ApiEnums.Network.vk && network != ApiEnums.Network.ok) {
            throw unsupported("geo", network);
        }
        if (options.poll() != null) {
            if (network != ApiEnums.Network.vk && network != ApiEnums.Network.ok && network != ApiEnums.Network.tg) {
                throw unsupported("poll", network);
            }
            List<String> answers = options.poll().answers();
            if (answers == null || answers.size() < 2) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.VALIDATION_FAILED,
                        "poll requires at least 2 answers");
            }
        }
    }

    private static ApiException unsupported(String option, ApiEnums.Network network) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemCodes.NETWORK_OPTION_UNSUPPORTED,
                option + " is not supported for network " + network.name());
    }
}

package ru.posts.api.ord;

import java.util.List;
import org.springframework.http.HttpStatus;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;

/** Validates ad marking payload and network-specific post options (BE-15). */
public final class AdMarkingValidator {

    private AdMarkingValidator() {}

    public static void requireValidWhenAd(boolean ad, OrdDtos.AdMarking marking) {
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
        if (!InnValidator.isValid(marking.inn())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.AD_INN_INVALID, "INN must be 10 or 12 digits");
        }
        if (marking.kktuCodes() == null || marking.kktuCodes().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "At least one KKTU code is required");
        }
        if ("juridical".equals(marking.orgType())
                && (marking.kpp() == null || marking.kpp().isBlank())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "KPP is required for juridical");
        }
        boolean selfPromo = Boolean.TRUE.equals(marking.selfPromo());
        if (!selfPromo && marking.contractDate() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "contractDate is required unless selfPromo");
        }
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

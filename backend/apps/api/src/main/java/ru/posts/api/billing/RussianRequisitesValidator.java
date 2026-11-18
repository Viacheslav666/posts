package ru.posts.api.billing;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.billing.BillingDtos;

/** Validates RU legal-entity requisites (INN checksum, KPP/OGRN length). */
@Component
public class RussianRequisitesValidator {

    public void validate(BillingDtos.BuyerRequisites buyer) {
        if (buyer == null) {
            throw invalid("Buyer requisites required");
        }
        if (!isValidInn(buyer.inn())) {
            throw invalid("Invalid INN checksum");
        }
        boolean legalEntity = buyer.inn().length() == 10;
        if (legalEntity) {
            if (buyer.kpp() == null || buyer.kpp().isBlank()) {
                throw invalid("KPP is required for legal entities (10-digit INN)");
            }
            if (!buyer.kpp().matches("\\d{9}")) {
                throw invalid("KPP must be 9 digits");
            }
        }
        if (buyer.ogrn() != null && !buyer.ogrn().isBlank()) {
            int len = buyer.ogrn().length();
            if (legalEntity && len != 13) {
                throw invalid("OGRN for legal entity must be 13 digits");
            }
            if (!legalEntity && len != 15) {
                throw invalid("OGRNIP for individual entrepreneur must be 15 digits");
            }
        }
    }

    static boolean isValidInn(String inn) {
        if (inn == null || !inn.matches("\\d{10}|\\d{12}")) {
            return false;
        }
        int[] digits = inn.chars().map(c -> c - '0').toArray();
        if (digits.length == 10) {
            int n10 = checksum(digits, new int[] {2, 4, 10, 3, 5, 9, 4, 6, 8});
            return n10 == digits[9];
        }
        int n11 = checksum(digits, new int[] {7, 2, 4, 10, 3, 5, 9, 4, 6, 8});
        int n12 = checksum(digits, new int[] {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8});
        return n11 == digits[10] && n12 == digits[11];
    }

    private static int checksum(int[] digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digits[i] * weights[i];
        }
        return (sum % 11) % 10;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.BILLING_REQUISITES_INVALID, message);
    }
}

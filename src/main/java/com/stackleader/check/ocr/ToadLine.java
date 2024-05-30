package com.stackleader.check.ocr;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import static org.apache.commons.validator.routines.checkdigit.ABANumberCheckDigit.ABAN_CHECK_DIGIT;

/**
 *
 * @author dcnorris
 */
@Data
@EqualsAndHashCode
public class ToadLine {

    private static final Pattern ROUTING_NUMBER_REGEX = Pattern.compile(".*?A(?<routingNumber>[0-9]{9})");
    private static final Pattern ACCOUNT_NUMBER_REGEX = Pattern.compile(".*?A(?<routingNumber>[0-9]{9})A(?<accountNumber>[0-9]+)");
    private static final Pattern CHECK_NUMBER_REGEX = Pattern.compile(".*?C(?<checkNumber>[0-9]+)C");
    private String checkNumber;
    private String routingNumber;
    private String accountNumber;
    private String amount;

    public ToadLine(String toadLine) {
        initializeFromToadLine(toadLine);

    }

    private void initializeFromToadLine(String toadLine) {
        routingNumber = extractRoutingNumber(toadLine);
        accountNumber = extractAccountNumber(toadLine);
        extractCheckNumber(toadLine).ifPresent(this::setCheckNumber);
    }

    private Optional<String> extractCheckNumber(String toadLine) {
        Matcher matcher = CHECK_NUMBER_REGEX.matcher(toadLine);
        if (matcher.find()) {
            String extractedCheckNumber = matcher.group("checkNumber");
            return Optional.ofNullable(extractedCheckNumber);
        }
        return Optional.empty();
    }

    private String extractRoutingNumber(String toadLine) {
        Matcher matcher = ROUTING_NUMBER_REGEX.matcher(toadLine);
        if (matcher.find()) {
            String extractedRoutingNumber = matcher.group("routingNumber");
            if (ABAN_CHECK_DIGIT.isValid(extractedRoutingNumber)) {
                return extractedRoutingNumber;
            }
        }

        throw new IllegalStateException("Could not extract routing number from image text");
    }

    private String extractAccountNumber(String toadLine) {
        Matcher matcher = ACCOUNT_NUMBER_REGEX.matcher(toadLine);
        if (matcher.find()) {
            String extractedAccountNumber = matcher.group("accountNumber");
            return extractedAccountNumber;
        }
        throw new IllegalStateException("Could not extract account number from image text");
    }

}

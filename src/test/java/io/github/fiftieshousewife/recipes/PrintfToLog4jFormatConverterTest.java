package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;

import static io.github.fiftieshousewife.recipes.PrintfToLog4jFormatConverter.*;
import static org.assertj.core.api.Assertions.assertThat;

class PrintfToLog4jFormatConverterTest {

    @Test
    void convert_singleSpecifier() {
        assertThat(convert("Value: %d%n")).isEqualTo("Value: {}");
    }

    @Test
    void convert_multipleSpecifiers() {
        assertThat(convert("Name: %s, Age: %d%n")).isEqualTo("Name: {}, Age: {}");
    }

    @Test
    void convert_escapedPercent() {
        assertThat(convert("100%%")).isEqualTo("100%");
    }

    @Test
    void convert_noSpecifiers() {
        assertThat(convert("Simple message")).isEqualTo("Simple message");
    }

    @Test
    void convert_newlineOnly() {
        assertThat(convert("Hello%n")).isEqualTo("Hello");
    }

    @Test
    void convert_trailingPercentSign() {
        assertThat(convert("50%")).isEqualTo("50%");
    }

    @Test
    void skipArgumentIndex_consumesIndexAndDollar() {
        assertThat(skipArgumentIndex("1$s", 0)).isEqualTo(2);
    }

    @Test
    void skipArgumentIndex_consumesMultiDigitIndex() {
        assertThat(skipArgumentIndex("12$s", 0)).isEqualTo(3);
    }

    @Test
    void skipArgumentIndex_doesNotConsumeWhenNoDollar() {
        assertThat(skipArgumentIndex("s", 0)).isEqualTo(0);
    }

    @Test
    void skipFlags_consumesFlags() {
        assertThat(skipFlags("-+s", 0)).isEqualTo(2);
    }

    @Test
    void skipFlags_doesNotConsumeNonFlags() {
        assertThat(skipFlags("s", 0)).isEqualTo(0);
    }

    @Test
    void skipWidth_consumesDigits() {
        assertThat(skipWidth("10s", 0)).isEqualTo(2);
    }

    @Test
    void skipWidth_doesNotConsumeNonDigits() {
        assertThat(skipWidth("s", 0)).isEqualTo(0);
    }

    @Test
    void skipPrecision_consumesDotAndDigits() {
        assertThat(skipPrecision(".5f", 0)).isEqualTo(2);
    }

    @Test
    void skipPrecision_doesNotConsumeWhenNoDot() {
        assertThat(skipPrecision("f", 0)).isEqualTo(0);
    }

    @Test
    void skipConversionChar_consumesSingleChar() {
        assertThat(skipConversionChar("s", 0)).isEqualTo(1);
    }

    @Test
    void skipConversionChar_consumesTwoCharsForDateTime() {
        assertThat(skipConversionChar("tH", 0)).isEqualTo(2);
    }

    @Test
    void skipConversionChar_handlesEmptyInput() {
        assertThat(skipConversionChar("", 0)).isEqualTo(0);
    }

    @Test
    void isDateTimeConversion_trueForLowercaseT() {
        assertThat(isDateTimeConversion('t')).isTrue();
    }

    @Test
    void isDateTimeConversion_trueForUppercaseT() {
        assertThat(isDateTimeConversion('T')).isTrue();
    }

    @Test
    void isDateTimeConversion_falseForOtherChar() {
        assertThat(isDateTimeConversion('s')).isFalse();
    }

    @Test
    void skipSpecifier_simpleConversion() {
        assertThat(skipSpecifier("s", 0)).isEqualTo(1);
    }

    @Test
    void skipSpecifier_withWidthAndPrecision() {
        assertThat(skipSpecifier("10.5s", 0)).isEqualTo(5);
    }

    @Test
    void skipSpecifier_withAllParts() {
        assertThat(skipSpecifier("1$-10.5s", 0)).isEqualTo(8);
    }
}

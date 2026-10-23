package com.museotek.box.web;

import com.museotek.box.domain.scanobject.CardColour;
import com.museotek.box.web.box.CreateBoxRequest;
import com.museotek.box.web.box.UpdateBoxRequest;
import com.museotek.box.web.scanobject.CreateColouredCardRequest;
import com.museotek.box.web.scanobject.CreateDraftRequest;
import com.museotek.box.web.scanobject.CreatePrintedImageRequest;
import com.museotek.box.web.scanobject.CreateThreeDPrintedObjectRequest;
import com.museotek.box.web.scanobject.UpdateColouredCardRequest;
import com.museotek.box.web.scanobject.UpdateDraftRequest;
import com.museotek.box.web.scanobject.UpdatePrintedImageRequest;
import com.museotek.box.web.scanobject.UpdateThreeDPrintedObjectRequest;
import com.museotek.box.web.scanobjecttype.CreateScanObjectTypeRequest;
import com.museotek.box.web.scanobjecttype.UpdateScanObjectTypeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The VARCHAR(255) columns of boxes, scan objects and scan object types are checked at the API
 * boundary, so a value that is too long is a 400 VALIDATION_ERROR instead of a database 500.
 */
class RequestLengthLimitsTest {

    private static final String OK = "x".repeat(255);
    private static final String TOO_LONG = "x".repeat(256);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    static Stream<Arguments> tooLongValues() {
        return Stream.of(
                Arguments.of(new CreateBoxRequest(TOO_LONG, "SN-1"), "name"),
                Arguments.of(new CreateBoxRequest("Box", TOO_LONG), "serialNumber"),
                Arguments.of(new UpdateBoxRequest(TOO_LONG, "SN-1"), "name"),
                Arguments.of(new UpdateBoxRequest("Box", TOO_LONG), "serialNumber"),
                Arguments.of(new CreateColouredCardRequest(TOO_LONG, null, false, null, CardColour.RED), "name"),
                Arguments.of(new CreateColouredCardRequest("Card", TOO_LONG, false, null, CardColour.RED), "rfidTag"),
                Arguments.of(new UpdateColouredCardRequest("Card", TOO_LONG, false, null, CardColour.RED), "rfidTag"),
                Arguments.of(new CreateDraftRequest(TOO_LONG, null, false, null), "name"),
                Arguments.of(new UpdateDraftRequest("Draft", TOO_LONG, false, null), "rfidTag"),
                Arguments.of(new CreatePrintedImageRequest("Image", null, false, null, TOO_LONG), "imageUrl"),
                Arguments.of(new UpdatePrintedImageRequest(TOO_LONG, null, false, null, "url"), "name"),
                Arguments.of(new CreateThreeDPrintedObjectRequest("Model", null, false, null, TOO_LONG), "modelRef"),
                Arguments.of(new UpdateThreeDPrintedObjectRequest("Model", TOO_LONG, false, null, "ref"), "rfidTag"),
                Arguments.of(new CreateScanObjectTypeRequest(TOO_LONG), "name"),
                Arguments.of(new UpdateScanObjectTypeRequest(TOO_LONG), "name"));
    }

    @ParameterizedTest
    @MethodSource("tooLongValues")
    void aValueOverTheColumnLength_isRejected(Object request, String field) {
        assertThat(invalidFields(request)).containsExactly(field);
    }

    static Stream<Object> valuesAtTheLimit() {
        return Stream.of(
                new CreateBoxRequest(OK, OK),
                new CreateColouredCardRequest(OK, OK, false, null, CardColour.RED),
                new CreatePrintedImageRequest(OK, OK, false, null, OK),
                new CreateThreeDPrintedObjectRequest(OK, OK, false, null, OK),
                new CreateScanObjectTypeRequest(OK));
    }

    @ParameterizedTest
    @MethodSource("valuesAtTheLimit")
    void exactlyTheColumnLength_isAccepted(Object request) {
        assertThat(invalidFields(request)).isEmpty();
    }

    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}

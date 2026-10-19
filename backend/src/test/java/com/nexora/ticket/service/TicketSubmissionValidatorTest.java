package com.nexora.ticket.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.nexora.category.entity.*;
import com.nexora.category.service.FormValidator;
import com.nexora.common.storage.*;
import com.nexora.ticket.dto.request.CreateTicketRequest;
import com.nexora.ticket.testsupport.TicketSubmissionFixtures;

import jakarta.validation.Validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.*;

class TicketSubmissionValidatorTest {
    static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    final FileStorageValidator files = spy(new FileStorageValidator());
    final jakarta.validation.Validator beans =
            Validation.buildDefaultValidatorFactory().getValidator();
    final TicketSubmissionValidator validator =
            new TicketSubmissionValidator(beans, new FormValidator(beans), JSON, files);

    static Stream<Arguments> cases() throws Exception {
        try (var input =
                TicketSubmissionValidatorTest.class.getResourceAsStream(
                        "/contracts/ticket-submission-values.json")) {
            var cases = JSON.readTree(input);
            return StreamSupport.stream(cases.spliterator(), false)
                    .map(c -> Arguments.of(c.toString(), c));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void sharesValueSemanticsWithFrontend(String name, JsonNode test) {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.valueOf(test.path("type").asText()),
                        test.get("rules"),
                        test.path("required").asBoolean());
        if (test.get("valid").asBoolean()) {
            var result =
                    validator.validate(
                            f.form(),
                            f.fields(),
                            f.options(),
                            f.request(test.get("value")),
                            List.of());
            assertThat(result.answers().values()).containsExactly(test.get("value"));
        } else
            assertFailure(
                    f,
                    f.request(test.get("value")),
                    List.of(),
                    HttpStatus.BAD_REQUEST,
                    "dynamicValues.answer");
    }

    @Test
    void optionalEmptyValuesAreOmittedWithoutTrimmingRealMultilineText() {
        for (JsonNode empty :
                List.of(
                        NullNode.instance,
                        TextNode.valueOf(""),
                        TextNode.valueOf(" \n\t"),
                        JSON.createArrayNode())) {
            var f =
                    TicketSubmissionFixtures.create(
                            DynamicFieldType.TEXT, JSON.createObjectNode(), false);
            assertThat(
                            validator
                                    .validate(
                                            f.form(),
                                            f.fields(),
                                            f.options(),
                                            f.request(empty),
                                            List.of())
                                    .answers())
                    .isEmpty();
            f.fields().getFirst().changeRequired(true);
            assertFailure(
                    f, f.request(empty), List.of(), HttpStatus.BAD_REQUEST, "dynamicValues.answer");
        }
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.TEXTAREA, JSON.createObjectNode(), false);
        var text = TextNode.valueOf("  a\nb  ");
        assertThat(
                        validator
                                .validate(
                                        f.form(),
                                        f.fields(),
                                        f.options(),
                                        f.request(text),
                                        List.of())
                                .answers()
                                .values())
                .containsExactly(text);
        verify(files, never()).validateCount(any());
    }

    @Test
    void doesNotCoerceWrongTypesOrAcceptDuplicateUnknownInactiveOrImageScalarKeys() {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.NUMBER, JSON.createObjectNode(), false);
        assertFailure(
                f,
                f.request(TextNode.valueOf("0")),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "dynamicValues.answer");
        assertFailure(
                f,
                f.request(
                        List.of(new CreateTicketRequest.DynamicValue("Answer", IntNode.valueOf(0))),
                        List.of()),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "dynamicValues.Answer");
        assertFailure(
                f,
                f.request(
                        List.of(
                                new CreateTicketRequest.DynamicValue("answer", IntNode.valueOf(0)),
                                new CreateTicketRequest.DynamicValue("answer", IntNode.valueOf(1))),
                        List.of()),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "dynamicValues.answer");
        f.fields().getFirst().changeActive(false);
        assertFailure(
                f,
                f.request(IntNode.valueOf(0)),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "dynamicValues.answer");
        var image =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.IMAGE, JSON.createObjectNode(), false);
        assertFailure(
                image,
                image.request(NullNode.instance),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "dynamicValues.answer");
    }

    @Test
    void usesExactBigDecimalModuloEvenBeyondDoubleRange() throws Exception {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.NUMBER,
                        JSON.readTree("{\"step\":0.00000000000000001}"),
                        false);
        var value = JSON.readTree("0.10000000000000001");
        assertThat(
                        validator
                                .validate(
                                        f.form(),
                                        f.fields(),
                                        f.options(),
                                        f.request(value),
                                        List.of())
                                .answers()
                                .values())
                .containsExactly(value);
        var big = JSON.readTree("1e400");
        assertThat(
                        validator
                                .validate(
                                        f.form(),
                                        f.fields(),
                                        f.options(),
                                        f.request(big),
                                        List.of())
                                .answers()
                                .values())
                .containsExactly(big);
    }

    @Test
    void distinguishesWrongCategoryStaleAndLegacyForms() throws Exception {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.DATETIME, JSON.createObjectNode(), false);
        var request = f.request(List.of(), List.of());
        assertFailure(
                f,
                new CreateTicketRequest(99L, 2L, 10L, null, "T", "D", List.of(), List.of()),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "formVersionId");
        assertFailure(
                f,
                new CreateTicketRequest(1L, 99L, 10L, null, "T", "D", List.of(), List.of()),
                List.of(),
                HttpStatus.CONFLICT,
                "formVersionId");
        f.fields().getFirst().changeValidationRules("{\"min\":\"2026-10-18T09:30:00\"}");
        assertFailure(
                f, request, List.of(), HttpStatus.UNPROCESSABLE_ENTITY, "dynamicValues.answer");
        f.fields().getFirst().changeValidationRules("{\"min\":\"2026-10-18T09:30:00+07:00\"}");
        assertThat(
                        validator
                                .validate(f.form(), f.fields(), f.options(), request, List.of())
                                .answers())
                .isEmpty();
    }

    @Test
    void validatesFiveUniqueImagesAndRetainsStrictGlobalEmptyCountContract() {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.IMAGE, JSON.createObjectNode(), true);
        f.form().changeMinAttachmentCount(5);
        List<MultipartFile> photos =
                IntStream.range(0, 5).mapToObj(i -> (MultipartFile) photo()).toList();
        var mappings =
                IntStream.range(0, 5)
                        .mapToObj(
                                i ->
                                        new CreateTicketRequest.Attachment(
                                                i, i == 0 ? "answer" : null))
                        .toList();
        var result =
                validator.validate(
                        f.form(), f.fields(), f.options(), f.request(List.of(), mappings), photos);
        assertThat(result.imageFields()).containsOnlyKeys(0);
        verify(files).validateCount(photos);
        assertThatThrownBy(() -> new FileStorageValidator().validateCount(List.of()))
                .isInstanceOf(InvalidFileException.class);
        assertFailure(
                f,
                f.request(List.of(), List.of()),
                List.of(),
                HttpStatus.BAD_REQUEST,
                "dynamicValues.answer");
        assertFailure(
                f,
                f.request(
                        List.of(),
                        List.of(
                                new CreateTicketRequest.Attachment(0, "answer"),
                                new CreateTicketRequest.Attachment(0, null))),
                List.of(photo(), photo()),
                HttpStatus.BAD_REQUEST,
                "attachments[1]");
        assertFailure(
                f,
                f.request(List.of(), List.of(new CreateTicketRequest.Attachment(5, null))),
                List.of(photo()),
                HttpStatus.BAD_REQUEST,
                "attachments[0]");
    }

    @Test
    void classifiesFileErrorsByTypedReasonWithoutWritingToDisk() throws Exception {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.IMAGE, JSON.createObjectNode(), false);
        var request =
                f.request(List.of(), List.of(new CreateTicketRequest.Attachment(0, "answer")));
        assertFailure(
                f,
                request,
                List.of(new MockMultipartFile("files", "x.png", "image/png", new byte[0])),
                HttpStatus.BAD_REQUEST,
                "attachments[0]");
        assertFailure(
                f,
                request,
                List.of(new MockMultipartFile("files", "x.png", "image/png", new byte[] {1})),
                HttpStatus.BAD_REQUEST,
                "attachments[0]");
        assertFailure(
                f,
                request,
                List.of(new MockMultipartFile("files", "x.gif", "image/gif", new byte[] {1})),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "attachments[0]");
        assertFailure(
                f,
                request,
                List.of(new MockMultipartFile("files", "x.png", "image/png", new byte[5242881])),
                HttpStatus.PAYLOAD_TOO_LARGE,
                "attachments[0]");
        f.fields().getFirst().changeValidationRules("{\"maxSizeBytes\":7}");
        assertFailure(f, request, List.of(photo()), HttpStatus.PAYLOAD_TOO_LARGE, "attachments[0]");
        f.fields().getFirst().changeValidationRules("{\"allowedTypes\":[\"image/jpeg\"]}");
        assertFailure(
                f, request, List.of(photo()), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "attachments[0]");
    }

    @Test
    void rejectsCombinedImageMinimumAboveFive() {
        var f =
                TicketSubmissionFixtures.create(
                        DynamicFieldType.IMAGE, JSON.createObjectNode(), true);
        var definitions = new ArrayList<>(f.fields());
        for (int i = 1; i < 6; i++) {
            var field =
                    new FieldDefinition(f.form(), "image" + i, "Image", DynamicFieldType.IMAGE, i);
            field.changeRequired(true);
            ReflectionTestUtils.setField(field, "id", (long) i + 3);
            definitions.add(field);
        }
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        f.form(),
                                        definitions,
                                        f.options(),
                                        f.request(List.of(), List.of()),
                                        List.of()))
                .isInstanceOfSatisfying(
                        TicketSubmissionException.class,
                        ex ->
                                assertThat(ex.getStatus())
                                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    static MockMultipartFile photo() {
        return new MockMultipartFile(
                "files",
                "evidence.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
    }

    void assertFailure(
            TicketSubmissionFixtures f,
            CreateTicketRequest request,
            List<MultipartFile> photos,
            HttpStatus status,
            String path) {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        f.form(), f.fields(), f.options(), request, photos))
                .isInstanceOfSatisfying(
                        TicketSubmissionException.class,
                        ex -> {
                            assertThat(ex.getStatus()).isEqualTo(status);
                            assertThat(ex.getFieldErrors()).containsKey(path);
                        });
    }
}

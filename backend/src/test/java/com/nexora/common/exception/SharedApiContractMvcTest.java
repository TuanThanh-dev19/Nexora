package com.nexora.common.exception;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nexora.common.concurrency.VersionedRequest;
import com.nexora.common.pagination.PageRequestFactory;
import com.nexora.common.pagination.PageResponse;
import com.nexora.common.validation.RequestParameterValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SharedApiContractMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ContractTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationUsesProblemDetailWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ProblemTypes.VALIDATION_FAILED.toString()))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"));
    }

    @Test
    void malformedJsonDoesNotExposeParserInternals() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ProblemTypes.MALFORMED_REQUEST.toString()))
                .andExpect(jsonPath("$.detail").value("The request body is missing or cannot be parsed."));
    }

    @Test
    void missingResourceUsesStableNotFoundProblem() throws Exception {
        assertProblem("/api/v1/contracts/not-found", 404, ProblemTypes.RESOURCE_NOT_FOUND);
    }

    @Test
    void businessRuleUsesUnprocessableEntityProblem() throws Exception {
        assertProblem("/api/v1/contracts/business-rule", 422, ProblemTypes.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void staleWriteUsesConflictProblemWithoutPersistenceDetails() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/stale-write"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ProblemTypes.STALE_WRITE.toString()))
                .andExpect(jsonPath("$.detail").value(
                        "The resource was changed by another request. Reload it and retry your update."));
    }

    @Test
    void authenticationAndAuthorizationProblemsDoNotExposeInternals() throws Exception {
        assertProblem("/api/v1/contracts/unauthenticated", 401, ProblemTypes.UNAUTHENTICATED);
        assertProblem("/api/v1/contracts/forbidden", 403, ProblemTypes.ACCESS_DENIED);
    }

    @Test
    void paginationIsZeroBasedAndUsesTheSharedResponseShape() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value("third"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void paginationDefaultsToTwentyAndRejectsSizesAboveOneHundred() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(20));

        mockMvc.perform(get("/api/v1/contracts/query").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_PAGINATION.toString()));
    }

    @Test
    void endpointSortAllowlistMapsPublicFieldsAndRejectsUnknownValues() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/query").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort[0].property").value("createdAt"))
                .andExpect(jsonPath("$.sort[0].direction").value("DESC"));

        mockMvc.perform(get("/api/v1/contracts/query").param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.UNSUPPORTED_SORT.toString()));
    }

    @Test
    void filterAllowlistRejectsUnknownValues() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/query").param("state", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.UNSUPPORTED_FILTER.toString()));
    }

    @Test
    void versionedMutationRequiresANonNegativeExpectedVersion() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/versioned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());

        mockMvc.perform(post("/api/v1/contracts/versioned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isNoContent());
    }

    private void assertProblem(String path, int statusCode, java.net.URI type) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type.toString()))
                .andExpect(jsonPath("$.instance").value(path));
    }

    @RestController
    @RequestMapping("/api/v1/contracts")
    static class ContractTestController {

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("The requested resource does not exist.");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("The requested operation is not allowed.");
        }

        @GetMapping("/stale-write")
        void staleWrite() {
            throw new StaleWriteException();
        }

        @GetMapping("/unauthenticated")
        void unauthenticated() {
            throw new InsufficientAuthenticationException("secret authentication detail");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("secret authorization detail");
        }

        @GetMapping("/page")
        PageResponse<String> page() {
            return PageResponse.from(new PageImpl<>(
                    List.of("third", "fourth"),
                    org.springframework.data.domain.PageRequest.of(1, 2),
                    5));
        }

        @GetMapping("/query")
        QueryContract query(
                @RequestParam(required = false) Integer page,
                @RequestParam(required = false) Integer size,
                @RequestParam(required = false) String sort,
                @RequestParam(required = false) String state) {
            RequestParameterValidator.requireAllowedFilter("state", state, Set.of("OPEN", "CLOSED"));
            Pageable pageable = PageRequestFactory.create(
                    page,
                    size,
                    sort == null ? null : List.of(sort),
                    Map.of("createdAt", "createdAt", "title", "title"));
            List<SortContract> orders = pageable.getSort().stream()
                    .map(order -> new SortContract(order.getProperty(), order.getDirection().name()))
                    .toList();
            return new QueryContract(pageable.getPageNumber(), pageable.getPageSize(), orders);
        }

        @PostMapping("/versioned")
        org.springframework.http.ResponseEntity<Void> versioned(@Valid @RequestBody UpdateRequest request) {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }

    record UpdateRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.PositiveOrZero
            Long version) implements VersionedRequest {
    }

    record QueryContract(int pageNumber, int pageSize, List<SortContract> sort) {
    }

    record SortContract(String property, String direction) {
    }
}

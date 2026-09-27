package com.nexora.common.exception;

import java.net.URI;

public final class ProblemTypes {

    public static final URI VALIDATION_FAILED = type("validation-failed");
    public static final URI MALFORMED_REQUEST = type("malformed-request");
    public static final URI RESOURCE_NOT_FOUND = type("resource-not-found");
    public static final URI BUSINESS_RULE_VIOLATION = type("business-rule-violation");
    public static final URI UNAUTHENTICATED = type("unauthenticated");
    public static final URI ACCESS_DENIED = type("access-denied");
    public static final URI STALE_WRITE = type("stale-write");
    public static final URI INVALID_PAGINATION = type("invalid-pagination");
    public static final URI UNSUPPORTED_SORT = type("unsupported-sort");
    public static final URI UNSUPPORTED_FILTER = type("unsupported-filter");

    private static final String BASE_URI = "https://nexora.app/problems/";

    private ProblemTypes() {
    }

    private static URI type(String slug) {
        return URI.create(BASE_URI + slug);
    }
}

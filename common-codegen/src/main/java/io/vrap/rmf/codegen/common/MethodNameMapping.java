package io.vrap.rmf.codegen.common;

import io.vrap.rmf.raml.model.resources.HttpMethod;
import io.vrap.rmf.raml.model.resources.Method;

/**
 * This interface defines a mapping from {@link Method#getMethod()} to platform specific method names.
 */
@FunctionalInterface
public interface MethodNameMapping {

    default String getMappedName(final Method method) {
        return getMappedName(method.getMethod());
    }

    String getMappedName(HttpMethod httpMethod);

    /**
     * Defines the following default mapping:
     * POST -> "save"
     * PUT -> "update"
     * GET -> "get"
     * DELETE -> "delete"
     * PATCH -> "patch"
     *
     * @return the default mapping
     */
    static MethodNameMapping of() {
        return httpMehod -> {
            switch (httpMehod) {
                case POST:
                    return "save";
                case PUT:
                    return "update";
                default:
                    return httpMehod.getLiteral().toLowerCase();
            }
        };
    }
}

package org.example.datn.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Arrays;

/**
 * Escapes HTML-significant characters in request parameters as a basic XSS
 * defence. JSON bodies are not touched here — output encoding on the FE and
 * Bean Validation remain the primary defences.
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return sanitize(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        return Arrays.stream(values).map(this::sanitize).toArray(String[]::new);
    }

    private String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

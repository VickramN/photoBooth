package com.example.photoBooth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            Supplier<CsrfToken> csrfToken) {
        // Masked token for anything rendered server-side (BREACH protection).
        this.xor.handle(request, response, csrfToken);
        // Force the token to load so the XSRF-TOKEN cookie gets written.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        // SPA sends the raw cookie value in the header: compare it as-is.
        // Anything else (e.g. a form field) goes through the masked path.
        return StringUtils.hasText(headerValue)
                ? this.plain.resolveCsrfTokenValue(request, csrfToken)
                : this.xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
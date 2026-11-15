package top.egon.cola.platform.tianji.agent.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import top.egon.cola.platform.tianji.agent.server.api.LinkedAgentRuntimeController;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;

/** Retains the verifier-bound issuer and a request-scoped SERVICE token digest, never the bearer. */
public final class VerifiedAgentIssuerFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE = VerifiedAgentIssuerFilter.class.getName() + ".issuer";
    static final String SERVICE_TOKEN_DIGEST = VerifiedAgentIssuerFilter.class.getName() + ".serviceTokenDigest";
    private static final String LINKED_SUBMIT_PATH = "/api/tianji/v1/agent-process-dispatches/submit";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.removeAttribute(ATTRIBUTE);
        request.removeAttribute(SERVICE_TOKEN_DIGEST);
        if (SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken authentication
                && authentication.isAuthenticated()) {
            try {
                request.setAttribute(ATTRIBUTE, authentication.verifiedIssuer().toString());
                if (authentication.getPrincipal() instanceof ServiceIdentityPrincipal && "POST".equals(request.getMethod())
                        && isLinkedSubmit(request)) {
                    String token = bearer(request);
                    if (token != null) request.setAttribute(SERVICE_TOKEN_DIGEST, digest(token));
                }
            }
            catch (IllegalStateException unavailable) { request.removeAttribute(ATTRIBUTE); }
        }
        try { chain.doFilter(request, response); }
        finally { request.removeAttribute(ATTRIBUTE); request.removeAttribute(SERVICE_TOKEN_DIGEST); }
    }

    private static boolean isLinkedSubmit(HttpServletRequest request) {
        return (request.getContextPath() + LINKED_SUBMIT_PATH).equals(request.getRequestURI())
                || (request.getContextPath() + LinkedAgentRuntimeController.PATH).equals(request.getRequestURI());
    }

    static String currentServiceToken(HttpServletRequest request) {
        if (request == null || !(request.getAttribute(SERVICE_TOKEN_DIGEST) instanceof String expected)
                || !expected.matches("[0-9a-f]{64}")) return null;
        String token = bearer(request);
        if (token == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                digest(token).getBytes(StandardCharsets.US_ASCII))) return null;
        return token;
    }

    private static String bearer(HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1 || headers.getFirst().length() < 7
                || !headers.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = headers.getFirst().substring(7).trim();
        return token.length() <= 16_384 && token.matches("[A-Za-z0-9._~+/-]+=*") ? token : null;
    }

    private static String digest(String token) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("TIANJI_SERVICE_TOKEN_DIGEST_UNAVAILABLE"); }
        finally { java.util.Arrays.fill(bytes, (byte) 0); }
    }
}

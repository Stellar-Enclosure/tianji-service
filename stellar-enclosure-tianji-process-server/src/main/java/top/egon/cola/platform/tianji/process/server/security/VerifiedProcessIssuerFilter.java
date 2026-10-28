package top.egon.cola.platform.tianji.process.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.io.IOException;

/** Retains only the verifier-bound issuer while Jianshen replaces the USER authentication token. */
public final class VerifiedProcessIssuerFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE = VerifiedProcessIssuerFilter.class.getName() + ".issuer";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.removeAttribute(ATTRIBUTE);
        if (SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken authentication
                && authentication.isAuthenticated()) {
            try { request.setAttribute(ATTRIBUTE, authentication.verifiedIssuer().toString()); }
            catch (IllegalStateException unavailable) { request.removeAttribute(ATTRIBUTE); }
        }
        try { chain.doFilter(request, response); }
        finally { request.removeAttribute(ATTRIBUTE); }
    }
}

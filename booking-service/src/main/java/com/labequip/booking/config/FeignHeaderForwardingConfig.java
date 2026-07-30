package com.labequip.booking.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The Gateway authenticates the *incoming* request and adds X-Auth-User/X-Auth-Roles headers.
 * When Booking Service then calls Equipment Service directly via Eureka/Feign (bypassing the
 * Gateway), those headers need to be forwarded manually so Equipment Service's own RoleGuard
 * still sees an authenticated caller instead of rejecting the internal call.
 */
public class FeignHeaderForwardingConfig {

    @Bean
    public RequestInterceptor authHeaderForwardingInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            copyHeader(request, requestTemplate, "X-Auth-User");
            copyHeader(request, requestTemplate, "X-Auth-Roles");
            copyHeader(request, requestTemplate, "X-Correlation-Id");
        };
    }

    private void copyHeader(HttpServletRequest request, feign.RequestTemplate requestTemplate, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            requestTemplate.header(name, value);
        }
    }
}

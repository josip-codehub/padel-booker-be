package org.padel.booker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * In every real deployment the app is same-origin (nginx serves the UI and proxies /api; ng serve
 * proxies /api too), so CORS isn't strictly needed. But the browser still sends an Origin header,
 * and a restrictive allow-list makes Spring reject it ("Invalid CORS request") when reached via a
 * tunnel port or a custom domain. Since the API is protected by a Bearer token and uses no cookies,
 * we allow any origin pattern (credentials disabled) to keep access working from any host/port.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(false);
    }
}

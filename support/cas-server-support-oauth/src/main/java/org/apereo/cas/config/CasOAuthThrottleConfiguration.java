package org.apereo.cas.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.support.oauth.OAuth20Constants;
import org.apereo.cas.support.oauth.authenticator.Authenticators;
import org.apereo.cas.support.oauth.web.OAuth20HandlerInterceptorAdapter;
import org.apereo.cas.support.oauth.web.response.accesstoken.ext.AccessTokenGrantRequestExtractor;
import org.apereo.cas.throttle.AuthenticationThrottlingExecutionPlan;
import org.apereo.cas.throttle.AuthenticationThrottlingExecutionPlanConfigurer;

import lombok.val;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.springframework.web.SecurityInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apereo.cas.support.oauth.OAuth20Constants.BASE_OAUTH20_URL;

/**
 * This is {@link CasOAuthThrottleConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@Configuration("oauthThrottleConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class CasOAuthThrottleConfiguration implements AuthenticationThrottlingExecutionPlanConfigurer {

    @Autowired
    @Qualifier("oauthSecConfig")
    private ObjectProvider<Config> oauthSecConfig;

    @Autowired
    @Qualifier("accessTokenGrantRequestExtractors")
    private Collection<AccessTokenGrantRequestExtractor> accessTokenGrantRequestExtractors;

    @ConditionalOnMissingBean(name = "requiresAuthenticationAuthorizeInterceptor")
    @Bean
    public SecurityInterceptor requiresAuthenticationAuthorizeInterceptor() {
        return new SecurityInterceptor(oauthSecConfig.getIfAvailable(), Authenticators.CAS_OAUTH_CLIENT);
    }

    @ConditionalOnMissingBean(name = "requiresAuthenticationAccessTokenInterceptor")
    @Bean
    public SecurityInterceptor requiresAuthenticationAccessTokenInterceptor() {
        val secConfig = oauthSecConfig.getIfAvailable();
        val clients = Objects.requireNonNull(secConfig).getClients()
            .findAllClients()
            .stream()
            .filter(client -> client instanceof DirectClient)
            .map(Client::getName)
            .collect(Collectors.joining(","));
        return new SecurityInterceptor(oauthSecConfig.getIfAvailable(), clients);
    }

    @ConditionalOnMissingBean(name = "oauthHandlerInterceptorAdapter")
    @Bean
    public HandlerInterceptor oauthHandlerInterceptorAdapter() {
        return new OAuth20HandlerInterceptorAdapter(
            requiresAuthenticationAccessTokenInterceptor(),
            requiresAuthenticationAuthorizeInterceptor(),
            accessTokenGrantRequestExtractors);
    }

    @Override
    public void configureAuthenticationThrottlingExecutionPlan(final AuthenticationThrottlingExecutionPlan plan) {
        plan.registerAuthenticationThrottleInterceptor(oauthHandlerInterceptorAdapter());
    }

    @Configuration("oauthThrottleWebMvcConfigurer")
    static class CasOAuthThrottleWebMvcConfigurer implements WebMvcConfigurer {

        @Autowired
        @Qualifier("authenticationThrottlingExecutionPlan")
        private ObjectProvider<AuthenticationThrottlingExecutionPlan> authenticationThrottlingExecutionPlan;

        @Override
        public void addInterceptors(final InterceptorRegistry registry) {
            Objects.requireNonNull(authenticationThrottlingExecutionPlan.getIfAvailable()).getAuthenticationThrottleInterceptors()
                .forEach(handler -> {
                    val baseUrl = BASE_OAUTH20_URL.concat("/");
                    registry.addInterceptor(handler)
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_AUTHORIZATION_REQUEST_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_CLAIMS_COLLECTION_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_JWKS_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_PERMISSION_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_POLICY_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_REGISTRATION_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.UMA_RESOURCE_SET_REGISTRATION_URL).concat("*"))

                        .addPathPatterns(baseUrl.concat(OAuth20Constants.AUTHORIZE_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.ACCESS_TOKEN_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.TOKEN_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.INTROSPECTION_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.CALLBACK_AUTHORIZE_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.DEVICE_AUTHZ_URL).concat("*"))
                        .addPathPatterns(baseUrl.concat(OAuth20Constants.PROFILE_URL).concat("*"));
                });
        }
    }

}

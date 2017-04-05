package org.apereo.cas.web.flow;

import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.Credential;
import org.apereo.cas.authentication.RememberMeCredential;
import org.apereo.cas.authentication.surrogate.SurrogateAuthenticationService;
import org.apereo.cas.authentication.SurrogateUsernamePasswordCredential;
import org.apereo.cas.authentication.UsernamePasswordCredential;
import org.apereo.cas.authentication.adaptive.AdaptiveAuthenticationPolicy;
import org.apereo.cas.web.flow.resolver.CasDelegatingWebflowEventResolver;
import org.apereo.cas.web.flow.resolver.CasWebflowEventResolver;
import org.apereo.cas.web.support.WebUtils;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

/**
 * This is {@link SurrogateInitialAuthenticationAction}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public class SurrogateInitialAuthenticationAction extends InitialAuthenticationAction {
    private final String separator;
    private final SurrogateAuthenticationService surrogateService;

    public SurrogateInitialAuthenticationAction(final CasDelegatingWebflowEventResolver delegatingWebflowEventResolver,
                                                final CasWebflowEventResolver webflowEventResolver,
                                                final AdaptiveAuthenticationPolicy adaptiveAuthenticationPolicy,
                                                final String separator,
                                                final SurrogateAuthenticationService surrogateService) {
        super(delegatingWebflowEventResolver, webflowEventResolver, adaptiveAuthenticationPolicy);
        this.separator = separator;
        this.surrogateService = surrogateService;
    }

    @Override
    protected Event doPreExecute(final RequestContext context) throws Exception {
        final UsernamePasswordCredential up = WebUtils.getCredential(context, UsernamePasswordCredential.class);
        if (up instanceof SurrogateUsernamePasswordCredential) {
            return null;
        }
        if (up.getUsername().contains(this.separator)) {
            convertToSurrogateCredential(context, up);
        }
        return null;
    }

    @Override
    protected void doPostExecute(final RequestContext context) throws Exception {
        deconvertFromSurrogatePrincipal(context);
    }

    private void convertToSurrogateCredential(final RequestContext context, final UsernamePasswordCredential up) {
        final SurrogateUsernamePasswordCredential sc = new SurrogateUsernamePasswordCredential();

        final String tUsername = up.getUsername();
        final String surrogateUsername = tUsername.substring(0, tUsername.indexOf(this.separator));
        final String realUsername = tUsername.substring(tUsername.indexOf(this.separator) + 1);

        if (StringUtils.isBlank(surrogateUsername)) {
            up.setUsername(realUsername);
            context.getFlowScope().put("requestSurrogateAccount", true);
            WebUtils.putCredential(context, up);
            return;
        }

        sc.setUsername(realUsername);
        sc.setSurrogateUsername(surrogateUsername);
        sc.setPassword(up.getPassword());
        if (up instanceof RememberMeCredential) {
            sc.setRememberMe(((RememberMeCredential) up).isRememberMe());
        }
        context.getFlowScope().put("requestSurrogateAccount", false);
        WebUtils.putCredential(context, sc);
    }

    private void deconvertFromSurrogatePrincipal(final RequestContext context) {
        final Credential c = WebUtils.getCredential(context);
        if (c instanceof SurrogateUsernamePasswordCredential) {
            final SurrogateUsernamePasswordCredential sc = SurrogateUsernamePasswordCredential.class.cast(c);
            final UsernamePasswordCredential up = new UsernamePasswordCredential();
            up.setUsername(sc.getUsername());
            up.setPassword(sc.getPassword());
            WebUtils.putCredential(context, up);
        }
    }
}

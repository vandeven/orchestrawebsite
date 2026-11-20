package org.example.website.platform.internal.access;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.NotAllowedException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.example.website.platform.api.access.Destructive;
import org.example.website.platform.api.errors.InvalidInput;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;

/**
 * Refuses a request to a {@link Destructive} endpoint unless it is a {@code POST} with the explicit
 * confirmation field (ARC-17). The server never trusts the dialog in the browser.
 */
@Destructive
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class DestructiveInterceptor {

    private static final Logger LOG = Logger.getLogger(DestructiveInterceptor.class);

    @Inject CurrentVertxRequest currentRequest;

    @AroundInvoke
    Object requireConfirmation(InvocationContext context) throws Exception {
        String httpMethod = currentRequest.getCurrent().request().method().name();
        if (!httpMethod.equals("POST")) {
            LOG.warnf("Destructive endpoint %s called with %s, refused", name(context), httpMethod);
            throw new NotAllowedException("POST");
        }
        String confirm = confirmationValue(context);
        if (!Destructive.CONFIRMED.equals(confirm)) {
            LOG.warnf(
                    "Destructive endpoint %s called without confirmation, refused", name(context));
            throw new InvalidInput("Missing or wrong confirmation field");
        }
        return context.proceed();
    }

    /** The value of the {@code confirm} form parameter of the called method. */
    private static String confirmationValue(InvocationContext context) {
        Method method = context.getMethod();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            RestForm form = parameters[i].getAnnotation(RestForm.class);
            if (form == null) {
                continue;
            }
            String field = form.value().isEmpty() ? parameters[i].getName() : form.value();
            if (field.equals(Destructive.FIELD)) {
                Object value = context.getParameters()[i];
                return value == null ? null : value.toString();
            }
        }
        throw new IllegalStateException(
                name(context) + " is @Destructive but has no @RestForm parameter named confirm");
    }

    private static String name(InvocationContext context) {
        return context.getMethod().getDeclaringClass().getSimpleName()
                + "."
                + context.getMethod().getName();
    }
}

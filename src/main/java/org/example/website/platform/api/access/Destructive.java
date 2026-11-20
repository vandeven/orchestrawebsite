package org.example.website.platform.api.access;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that deletes or removes something (ARC-17, plan: Confirming destructive
 * actions). Such an endpoint must be a {@code POST} and have a form parameter named {@code confirm}
 * (a {@code @RestForm String confirm}). The request is refused unless it is a {@code POST} whose
 * {@code confirm} field is exactly {@value #CONFIRMED}, so it can never happen through a link or as
 * the side effect of another request.
 */
@Documented
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Destructive {

    /** The name of the confirmation form field. */
    String FIELD = "confirm";

    /** The only value of the confirmation field that counts as confirmed. */
    String CONFIRMED = "yes";
}

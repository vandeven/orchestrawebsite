package org.example.website.platform.api.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares who may open an endpoint or page (ARC-7). On a method it overrides the class. An
 * endpoint without this annotation is refused: deny by default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Access {
    AccessLevel value();
}

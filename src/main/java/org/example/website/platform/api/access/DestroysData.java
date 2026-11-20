package org.example.website.platform.api.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation of an {@code api} that deletes or removes something, or changes who may do
 * what (plan: Confirming destructive actions). A web method that calls it must be annotated with
 * {@link Destructive}; an architecture test checks that.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DestroysData {}

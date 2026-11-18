package org.example.website;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import java.util.List;
import java.util.Map;
import org.example.website.platform.api.Access;
import org.example.website.platform.api.Destructive;
import org.jboss.resteasy.reactive.RestForm;

/** Architecture rules from docs/architecture.md. More rules are added with each component. */
@AnalyzeClasses(
        packages = "org.example.website",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "org.example.website.";

    private static final List<String> COMPONENTS =
            List.of("platform", "audit", "accounts", "content", "songs", "contact", "site");

    /** ARC-4: the components a component may depend on. Everything else is forbidden. */
    private static final Map<String, List<String>> ALLOWED =
            Map.of(
                    "platform", List.of(),
                    "audit", List.of("platform"),
                    "accounts", List.of("platform", "audit"),
                    "content", List.of("platform", "audit", "accounts"),
                    "songs", List.of("platform", "audit", "accounts"),
                    "contact", List.of("platform", "audit", "accounts"),
                    "site", List.of("platform", "accounts", "content", "contact"));

    /** ARC-2: every class is in exactly one of the components. */
    @ArchTest
    static final ArchRule everyClassBelongsToAComponent =
            classes()
                    .that()
                    .resideInAPackage("org.example.website..")
                    .should()
                    .resideInAnyPackage(
                            COMPONENTS.stream().map(c -> ROOT + c + "..").toArray(String[]::new));

    /** ARC-4: dependencies are one-way and only between the allowed components (so no cycles). */
    @ArchTest
    static void componentsOnlyDependOnTheAllowedComponents(JavaClasses classes) {
        for (String component : COMPONENTS) {
            for (String other : COMPONENTS) {
                if (other.equals(component) || ALLOWED.get(component).contains(other)) {
                    continue;
                }
                noClasses()
                        .that()
                        .resideInAPackage(ROOT + component + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage(ROOT + other + "..")
                        .as(component + " does not depend on " + other)
                        .allowEmptyShould(true)
                        .check(classes);
            }
        }
    }

    /** ARC-3: another component only uses the api package, never the internal or web packages. */
    @ArchTest
    static void otherComponentsOnlyUseTheApi(JavaClasses classes) {
        for (String component : COMPONENTS) {
            noClasses()
                    .that()
                    .resideOutsideOfPackage(ROOT + component + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + component + ".internal..", ROOT + component + ".web..")
                    .as("only " + component + " itself uses its internal and web packages")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    /** ARC-3: web classes only call the api of their component, never its implementation. */
    @ArchTest
    static final ArchRule webClassesDoNotUseInternalClasses =
            noClasses()
                    .that()
                    .resideInAPackage("..web..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..internal..");

    /** ARC-6: the api package depends on nothing of its own component's internal or web code. */
    @ArchTest
    static final ArchRule apiDoesNotDependOnImplementation =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..internal..", "..web..");

    private static final DescribedPredicate<JavaMethod> IS_ENDPOINT =
            DescribedPredicate.describe(
                    "an endpoint (a JAX-RS method)",
                    method ->
                            method.isAnnotatedWith(GET.class)
                                    || method.isAnnotatedWith(POST.class)
                                    || method.isAnnotatedWith(PUT.class)
                                    || method.isAnnotatedWith(DELETE.class)
                                    || method.isAnnotatedWith(PATCH.class)
                                    || method.isAnnotatedWith(HEAD.class));

    /** ARC-7: every endpoint has an @Access, on the method or on its class. */
    @ArchTest
    static final ArchRule everyEndpointDeclaresItsAccess =
            methods()
                    .that(IS_ENDPOINT)
                    .should(
                            new ArchCondition<JavaMethod>(
                                    "have @Access on the method or the class") {
                                @Override
                                public void check(JavaMethod method, ConditionEvents events) {
                                    boolean declared =
                                            method.isAnnotatedWith(Access.class)
                                                    || method.getOwner()
                                                            .isAnnotatedWith(Access.class);
                                    if (!declared) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        method,
                                                        method.getFullName() + " has no @Access"));
                                    }
                                }
                            });

    /** ARC-17: a destructive endpoint is a POST and has the confirm form field. */
    @ArchTest
    static final ArchRule destructiveEndpointsArePostWithAConfirmField =
            methods()
                    .that()
                    .areAnnotatedWith(Destructive.class)
                    .should(
                            new ArchCondition<JavaMethod>(
                                    "be a POST with a confirm form parameter") {
                                @Override
                                public void check(JavaMethod method, ConditionEvents events) {
                                    boolean post = method.isAnnotatedWith(POST.class);
                                    boolean hasConfirm =
                                            method.reflect().getParameters().length > 0
                                                    && java.util.Arrays.stream(
                                                                    method.reflect()
                                                                            .getParameters())
                                                            .anyMatch(p ->
                                                                    p.isAnnotationPresent(
                                                                                    RestForm.class)
                                                                            && "confirm"
                                                                                    .equals(
                                                                                            p
                                                                                                    .getName()));
                                    if (!post || !hasConfirm) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        method,
                                                        method.getFullName()
                                                                + " must be a @POST with a"
                                                                + " @RestForm confirm parameter"));
                                    }
                                }
                            })
                    .allowEmptyShould(true); // there are no destructive endpoints yet
}

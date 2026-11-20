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
import com.tngtech.archunit.lang.CompositeArchRule;
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
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.DestroysData;
import org.example.website.platform.api.access.Destructive;
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

    // Inside the accounts component the implementation is split by purpose, and the packages form
    // layers (see docs/architecture.md). The rules keep the flow readable: a lower layer never
    // reaches up to a higher one, and the sibling features do not know each other.

    private static final String ACCOUNTS = ROOT + "accounts.internal.";
    private static final String ACCOUNTS_ROOT = ROOT + "accounts.internal";

    private static ArchRule accountsPackageNeverUses(String from, String... forbidden) {
        String[] names = new String[forbidden.length];
        for (int i = 0; i < forbidden.length; i++) {
            names[i] = forbidden[i].isEmpty() ? ACCOUNTS_ROOT : ACCOUNTS + forbidden[i] + "..";
        }
        return noClasses()
                .that()
                .resideInAPackage(ACCOUNTS + from + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(names)
                .as(
                        "accounts "
                                + from
                                + " never uses "
                                + String.join(", ", forbidden).replaceAll(", $", "")
                                + (forbidden[forbidden.length - 1].isEmpty()
                                        ? " (nor the start-up classes)"
                                        : ""));
    }

    /** The configuration is at the bottom: it depends on nothing else of accounts. */
    @ArchTest
    static final ArchRule accountsConfigIsAtTheBottom =
            accountsPackageNeverUses(
                    "config", "passwords", "security", "store", "login", "recovery", "users");

    /** Passwords and security helpers are building blocks: they know no feature and no store. */
    @ArchTest
    static final ArchRule accountsBuildingBlocksKnowNoFeature =
            CompositeArchRule.of(
                            accountsPackageNeverUses(
                                    "passwords", "store", "login", "recovery", "users", ""))
                    .and(
                            accountsPackageNeverUses(
                                    "security", "store", "login", "recovery", "users", ""));

    /**
     * The data and its stores know no feature: login, recovery and users use them, not the reverse.
     */
    @ArchTest
    static final ArchRule accountsStoresKnowNoFeature =
            accountsPackageNeverUses("store", "passwords", "login", "recovery", "users", "");

    /** The in-memory and the Firestore stores are two alternatives: neither knows the other. */
    @ArchTest
    static final ArchRule accountsStoreImplementationsAreSeparate =
            CompositeArchRule.of(
                            accountsPackageNeverUses(
                                    "store.memory",
                                    "store.firestore",
                                    "login",
                                    "recovery",
                                    "users",
                                    ""))
                    .and(
                            accountsPackageNeverUses(
                                    "store.firestore",
                                    "store.memory",
                                    "login",
                                    "recovery",
                                    "users",
                                    ""));

    /** Only the store package itself (AccountsStorage) chooses between the two implementations. */
    @ArchTest
    static final ArchRule accountsOnlyTheStoreChoosesAnImplementation =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(ACCOUNTS + "store..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            ACCOUNTS + "store.memory..", ACCOUNTS + "store.firestore..");

    /**
     * Login and recovery are separate features: neither knows the other, and neither knows users.
     */
    @ArchTest
    static final ArchRule accountsLoginAndRecoveryAreSeparate =
            CompositeArchRule.of(accountsPackageNeverUses("login", "recovery", "users", ""))
                    .and(accountsPackageNeverUses("recovery", "login", "users", ""));

    /** Users (user management, my account, the master account) is the top layer. */
    @ArchTest
    static final ArchRule accountsUsersIsOnTop =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            ACCOUNTS + "config..",
                            ACCOUNTS + "passwords..",
                            ACCOUNTS + "security..",
                            ACCOUNTS + "store..",
                            ACCOUNTS + "login..",
                            ACCOUNTS + "recovery..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(ACCOUNTS + "users..");

    // The same idea for platform: its implementation packages are independent of each other (they
    // only share the api), and the api packages have a simple order, with errors at the bottom.

    private static final String PLATFORM = ROOT + "platform.";
    private static final List<String> PLATFORM_INTERNAL =
            List.of("access", "http", "mail", "database", "startup");

    /** Each implementation package of platform stands on its own: it only uses the api. */
    @ArchTest
    static final ArchRule platformImplementationPackagesAreIndependent =
            platformInternalPackagesDoNotUseEachOther();

    private static ArchRule platformInternalPackagesDoNotUseEachOther() {
        CompositeArchRule rule = null;
        for (String from : PLATFORM_INTERNAL) {
            String[] others =
                    PLATFORM_INTERNAL.stream()
                            .filter(other -> !other.equals(from))
                            .map(other -> PLATFORM + "internal." + other + "..")
                            .toArray(String[]::new);
            ArchRule single =
                    noClasses()
                            .that()
                            .resideInAPackage(PLATFORM + "internal." + from + "..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(others)
                            .as(
                                    "platform internal "
                                            + from
                                            + " does not use another internal package");
            rule = rule == null ? CompositeArchRule.of(single) : rule.and(single);
        }
        return rule;
    }

    /** Errors are the bottom of the platform api: they know none of the other api packages. */
    @ArchTest
    static final ArchRule platformErrorsKnowNoOtherApiPackage =
            noClasses()
                    .that()
                    .resideInAPackage(PLATFORM + "api.errors..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            PLATFORM + "api.access..",
                            PLATFORM + "api.mail..",
                            PLATFORM + "api.request..",
                            PLATFORM + "api.support..",
                            PLATFORM + "api.database..");

    /** The other api packages do not know each other, except that they may report errors. */
    @ArchTest
    static final ArchRule platformApiPackagesAreIndependent =
            platformApiPackagesDoNotUseEachOther();

    private static ArchRule platformApiPackagesDoNotUseEachOther() {
        List<String> packages = List.of("access", "mail", "request", "support", "database");
        CompositeArchRule rule = null;
        for (String from : packages) {
            String[] others =
                    packages.stream()
                            .filter(other -> !other.equals(from))
                            .map(other -> PLATFORM + "api." + other + "..")
                            .toArray(String[]::new);
            ArchRule single =
                    noClasses()
                            .that()
                            .resideInAPackage(PLATFORM + "api." + from + "..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(others)
                            .as("platform api " + from + " does not use another api package");
            rule = rule == null ? CompositeArchRule.of(single) : rule.and(single);
        }
        return rule;
    }

    // The accounts api is split by feature too. Users (the people and their roles) are the base,
    // login uses them, recovery and the rules stand alone.

    private static final String ACCOUNTS_API = ROOT + "accounts.api.";

    /** The api packages of accounts: users, rules and recovery know no other api package. */
    @ArchTest
    static final ArchRule accountsApiBaseKnowsNoOtherApiPackage =
            accountsApiNeverUses("users", "login", "recovery", "rules")
                    .and(accountsApiNeverUses("rules", "login", "recovery", "users"))
                    .and(accountsApiNeverUses("recovery", "login", "users", "rules"));

    /** Login may use users, and nothing else of the accounts api. */
    @ArchTest
    static final ArchRule accountsApiLoginOnlyUsesUsers =
            accountsApiNeverUses("login", "recovery", "rules");

    private static CompositeArchRule accountsApiNeverUses(String from, String... forbidden) {
        String[] names = new String[forbidden.length];
        for (int i = 0; i < forbidden.length; i++) {
            names[i] = ACCOUNTS_API + forbidden[i] + "..";
        }
        return CompositeArchRule.of(
                noClasses()
                        .that()
                        .resideInAPackage(ACCOUNTS_API + from + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(names)
                        .as(
                                "accounts api "
                                        + from
                                        + " does not use "
                                        + String.join(", ", forbidden)));
    }

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

    /**
     * ARC-17: a web method that calls an operation that destroys data or changes rights must be
     * annotated with {@code @Destructive}, so the server refuses it without a confirmation.
     */
    @ArchTest
    static final ArchRule webMethodsThatDestroyDataAreDestructive =
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("..web..")
                    .should(
                            new ArchCondition<JavaMethod>(
                                    "be @Destructive when they call an operation marked @DestroysData") {
                                @Override
                                public void check(JavaMethod method, ConditionEvents events) {
                                    boolean destroys =
                                            method.getMethodCallsFromSelf().stream()
                                                    .anyMatch(call ->
                                                            call.getTarget()
                                                                    .resolveMember()
                                                                    .map(target ->
                                                                            target.isAnnotatedWith(
                                                                                    DestroysData
                                                                                            .class))
                                                                    .orElse(false));
                                    if (destroys && !method.isAnnotatedWith(Destructive.class)) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        method,
                                                        method.getFullName()
                                                                + " destroys data but is not @Destructive"));
                                    }
                                }
                            });
}

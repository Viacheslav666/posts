package ru.posts.testsupport.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.dependOnClassesThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * ArchUnit rules from {@code docs/specs/02-backend.md} §1 item 8 and ADR-0002.
 *
 * <ul>
 *   <li>{@code domain} must not depend on Spring Web / AMQP / JPA adapter details
 *   <li>{@code apps/*} must not depend on each other
 *   <li>social adapters may depend only on {@code social-api}, {@code common}, {@code ratelimit}
 *       among Posts packages
 *   <li>ORD adapters may depend only on {@code ord-api}, {@code common}, {@code ratelimit} among
 *       Posts packages
 * </ul>
 */
public final class PostsArchitectureRules {

    private static final List<String> APP_PACKAGES = List.of(
            "ru.posts.api..",
            "ru.posts.scheduler..",
            "ru.posts.publisher..",
            "ru.posts.media..",
            "ru.posts.ingest..",
            "ru.posts.notifications..");

    private static final List<String> SOCIAL_ADAPTER_PACKAGES = List.of(
            "ru.posts.social.vk..", "ru.posts.social.telegram..", "ru.posts.social.ok..", "ru.posts.social.max..");

    private static final List<String> ORD_ADAPTER_PACKAGES = List.of("ru.posts.ord.vk..", "ru.posts.ord.yandex..");

    private PostsArchitectureRules() {}

    public static List<String> appPackages() {
        return APP_PACKAGES;
    }

    public static List<String> socialAdapterPackages() {
        return SOCIAL_ADAPTER_PACKAGES;
    }

    public static List<String> ordAdapterPackages() {
        return ORD_ADAPTER_PACKAGES;
    }

    public static ArchRule domainMustNotDependOnFrameworkAdapters() {
        return noClasses()
                .that()
                .resideInAPackage("ru.posts.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.amqp..",
                        "org.springframework.data.jpa..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "org.hibernate..")
                .because("domain must not depend on Spring Web/AMQP/JPA adapter details")
                .allowEmptyShould(true);
    }

    public static ArchRule domainMustNotDependOnApps() {
        return noClasses()
                .that()
                .resideInAPackage("ru.posts.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(APP_PACKAGES.toArray(String[]::new))
                .because("domain must not depend on apps")
                .allowEmptyShould(true);
    }

    /** Pairwise: each app package must not depend on the other app packages. */
    public static List<ArchRule> appIsolationRules() {
        return APP_PACKAGES.stream()
                .map(app -> {
                    String[] others =
                            APP_PACKAGES.stream().filter(p -> !p.equals(app)).toArray(String[]::new);
                    return noClasses()
                            .that()
                            .resideInAPackage(app)
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(others)
                            .because(app + " must not depend on other apps")
                            .allowEmptyShould(true);
                })
                .toList();
    }

    /** Per-adapter: only social-api, common, ratelimit (+ own package) among Posts packages. */
    public static List<ArchRule> socialAdapterIsolationRules() {
        return SOCIAL_ADAPTER_PACKAGES.stream()
                .map(adapter -> {
                    DescribedPredicate<JavaClass> allowed = resideInAnyPackage(
                            adapter, "ru.posts.social.api..", "ru.posts.common..", "ru.posts.ratelimit..");
                    DescribedPredicate<JavaClass> forbidden =
                            resideInAPackage("ru.posts..").and(DescribedPredicate.not(allowed));
                    return noClasses()
                            .that()
                            .resideInAPackage(adapter)
                            .should(dependOnClassesThat(forbidden))
                            .because(adapter
                                    + " may only depend on social-api, common, ratelimit"
                                    + " among Posts packages")
                            .allowEmptyShould(true);
                })
                .toList();
    }

    /** Per-adapter: only ord-api, common, ratelimit (+ own package) among Posts packages. */
    public static List<ArchRule> ordAdapterIsolationRules() {
        return ORD_ADAPTER_PACKAGES.stream()
                .map(adapter -> {
                    DescribedPredicate<JavaClass> allowed = resideInAnyPackage(
                            adapter, "ru.posts.ord.api..", "ru.posts.common..", "ru.posts.ratelimit..");
                    DescribedPredicate<JavaClass> forbidden =
                            resideInAPackage("ru.posts..").and(DescribedPredicate.not(allowed));
                    return noClasses()
                            .that()
                            .resideInAPackage(adapter)
                            .should(dependOnClassesThat(forbidden))
                            .because(adapter
                                    + " may only depend on ord-api, common, ratelimit"
                                    + " among Posts packages")
                            .allowEmptyShould(true);
                })
                .toList();
    }

    /**
     * BE-23 / ADR-0012: {@code /_control} may only appear in {@code ..emulator..} packages of social
     * adapters (never in production API reconciliation).
     */
    public static void assertControlApiOnlyInEmulatorPackages(JavaClasses classes) {
        for (JavaClass clazz : classes) {
            String pkg = clazz.getPackageName();
            if (!pkg.startsWith("ru.posts.social.")) {
                continue;
            }
            if (pkg.contains(".emulator")) {
                continue;
            }
            assertClassFileDoesNotContainControlPath(clazz);
        }
    }

    private static void assertClassFileDoesNotContainControlPath(JavaClass clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        ClassLoader loader = null;
        try {
            loader = clazz.reflect().getClassLoader();
        } catch (Exception ignored) {
            // fall through
        }
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader == null) {
            return;
        }
        try (java.io.InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            byte[] bytes = in.readAllBytes();
            String latin1 = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (latin1.contains("/_control")) {
                throw new AssertionError(
                        clazz.getName() + " contains '/_control' outside emulator package (BE-23 / ADR-0012)");
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception ignored) {
            // ignore unreadable classes
        }
    }

    public static void checkAll(JavaClasses classes) {
        domainMustNotDependOnFrameworkAdapters().check(classes);
        domainMustNotDependOnApps().check(classes);
        for (ArchRule rule : appIsolationRules()) {
            rule.check(classes);
        }
        for (ArchRule rule : socialAdapterIsolationRules()) {
            rule.check(classes);
        }
        for (ArchRule rule : ordAdapterIsolationRules()) {
            rule.check(classes);
        }
        assertControlApiOnlyInEmulatorPackages(classes);
    }

    /** Applies only rules that match the given production packages (module-local tests). */
    public static void checkRelevant(JavaClasses classes, String... packages) {
        for (String pkg : packages) {
            if (pkg.equals("ru.posts.domain") || pkg.startsWith("ru.posts.domain.")) {
                domainMustNotDependOnFrameworkAdapters().check(classes);
                domainMustNotDependOnApps().check(classes);
                continue;
            }
            for (String app : APP_PACKAGES) {
                String appRoot = app.replace("..", "");
                if (pkg.equals(appRoot) || pkg.startsWith(appRoot + ".")) {
                    for (ArchRule rule : appIsolationRules()) {
                        if (rule.getDescription().startsWith(app)) {
                            rule.check(classes);
                        }
                    }
                }
            }
            for (int i = 0; i < SOCIAL_ADAPTER_PACKAGES.size(); i++) {
                String adapter = SOCIAL_ADAPTER_PACKAGES.get(i);
                String adapterRoot = adapter.replace("..", "");
                if (pkg.equals(adapterRoot) || pkg.startsWith(adapterRoot + ".")) {
                    socialAdapterIsolationRules().get(i).check(classes);
                    assertControlApiOnlyInEmulatorPackages(classes);
                }
            }
            for (int i = 0; i < ORD_ADAPTER_PACKAGES.size(); i++) {
                String adapter = ORD_ADAPTER_PACKAGES.get(i);
                String adapterRoot = adapter.replace("..", "");
                if (pkg.equals(adapterRoot) || pkg.startsWith(adapterRoot + ".")) {
                    ordAdapterIsolationRules().get(i).check(classes);
                }
            }
        }
    }
}

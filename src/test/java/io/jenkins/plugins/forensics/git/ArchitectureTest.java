package io.jenkins.plugins.forensics.git;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * Defines several architecture rules for the static analysis model and parsers.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("hideutilityclassconstructor")
@AnalyzeClasses(packages = "io.jenkins.plugins.forensics.git..")
class ArchitectureTest {
    /** Test classes should not be public (Junit 5). */
    @ArchTest
    static final ArchRule NO_PUBLIC_TEST_CLASSES =
            noClasses().that().haveSimpleNameEndingWith("Test")
                    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                    .and(doNot(have(simpleNameEndingWith("ITest"))))
                    .should().bePublic();

    /**
     * Methods or constructors that are annotated with {@link VisibleForTesting} must not be called by other classes.
     * These methods are meant to be {@code private}. Only test classes are allowed to call these methods.
     */
    @ArchTest
    static final ArchRule NO_TEST_API_CALLED =
            noClasses().that().haveSimpleNameNotEndingWith("Test")
                    .should().callCodeUnitWhere(new AccessRestrictedToTests());

    /** Prevents that classes use visible but forbidden API. */
    @ArchTest
    static final ArchRule NO_FORBIDDEN_PACKAGE_ACCESSED =
            noClasses()
            .should().accessClassesThat().resideInAnyPackage(
                    "org.apache.commons.lang..", "javax.xml.bind..", "javax.annotation..");

    /** Prevents that classes use visible but forbidden API. */
    @ArchTest
    static final ArchRule NO_FORBIDDEN_CLASSES_CALLED
            = noClasses()
            .should().callCodeUnitWhere(new TargetIsForbiddenClass(
                    "org.junit.jupiter.api.Assertions", "org.junit.Assert"));

    /**
     * Matches if a code unit of one of the registered classes has been called.
     */
    private static class TargetIsForbiddenClass extends DescribedPredicate<JavaCall<?>> {
        private final String[] classes;

        TargetIsForbiddenClass(final String... classes) {
            super("forbidden class");

            this.classes = Arrays.copyOf(classes, classes.length);
        }

        @Override
        public boolean apply(final JavaCall<?> input) {
            return StringUtils.containsAny(input.getTargetOwner().getFullName(), classes)
                    && !input.getName().equals("assertTimeoutPreemptively");
        }
    }
}

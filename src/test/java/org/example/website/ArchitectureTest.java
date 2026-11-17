package org.example.website;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture rules from docs/architecture.md. More rules are added with each component. */
@AnalyzeClasses(packages = "org.example.website", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule platformDependsOnNoOtherComponent = noClasses()
            .that().resideInAPackage("..platform..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..audit..", "..accounts..", "..content..", "..songs..", "..contact..", "..site..");
}

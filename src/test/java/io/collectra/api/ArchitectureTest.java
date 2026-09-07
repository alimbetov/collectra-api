package io.collectra.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "io.collectra.api",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_API_OR_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.*.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.collectra.api.*.api..",
                            "io.collectra.api.*.infrastructure..");
}

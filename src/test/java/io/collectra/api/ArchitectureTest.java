package io.collectra.api;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ImportOption; import com.tngtech.archunit.junit.*;
@AnalyzeClasses(packages="io.collectra.api",importOptions=ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final com.tngtech.archunit.lang.ArchRule DOMAIN_DOES_NOT_DEPEND_ON_API_OR_INFRA=noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("..api..","..infrastructure..");
}

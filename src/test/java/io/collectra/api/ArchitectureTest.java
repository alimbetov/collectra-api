package io.collectra.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.collectra.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_API_OR_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.*.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.collectra.api.*.api..", "io.collectra.api.*.infrastructure..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_SPRING_WEB =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.*.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.springframework.web..");

    @ArchTest
    static final ArchRule API_DOES_NOT_DEPEND_DIRECTLY_ON_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.*.api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("io.collectra.api.*.infrastructure..");

    @ArchTest
    static final ArchRule COMMUNICATION_DOMAIN_DOES_NOT_DEPEND_ON_DELIVERY_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.communication.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.collectra.api.campaign.application..",
                            "org.springframework.amqp..",
                            "org.springframework.web..");

    @ArchTest
    static final ArchRule COMMUNICATION_DOMAIN_DOES_NOT_DEPEND_ON_APPLICATION =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.communication.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("io.collectra.api.communication.application..");

    @ArchTest
    static final ArchRule COMMUNICATION_APPLICATION_DOES_NOT_DEPEND_ON_KUMOMTA =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.communication.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("io.collectra.api.communication.infrastructure.kumomta..");

    @ArchTest
    static final ArchRule COMMUNICATION_APPLICATION_DOES_NOT_DEPEND_ON_MESSAGING_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.communication.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("io.collectra.api.communication.infrastructure.messaging..");

    @ArchTest
    static final ArchRule COMMUNICATION_APPLICATION_DOES_NOT_DEPEND_ON_SIMULATION_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("io.collectra.api.communication.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("io.collectra.api.communication.infrastructure.simulation..");
}

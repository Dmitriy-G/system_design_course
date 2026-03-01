package com.example.online.shop.arch;

import com.example.online.shop.OrderApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = OrderApplication.class,
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        }
)
public class LayerDependencyTest {

    @ArchTest
    static final ArchRule apiDependsOnlyFromServices =
            classes().that().resideInAnyPackage("..api..", "..api")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..api..", "..api", "..service..", "..service", "..validation..", "..validation", "java..", "org.springframework..")
                    .as("API layer should only depend on service layer");

    @ArchTest
    static final ArchRule communicationDependsOnlyFromCommunications =
            classes().that().resideInAnyPackage("..communications..", "..communications")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..communications..", "..communications", "java..", "org.springframework..")
                    .as("Communications layer should only depend on communications");


    @ArchTest
    static final ArchRule infrastructureDependsOnlyFromInfrastructure =
            classes().that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure", "..infrastructure..", "java..", "org.springframework..")
                    .as("Infrastructure layer must depend only infrastructure");

    @ArchTest
    static final ArchRule validationDoesNotDependsOnServices =
            noClasses().that().resideInAnyPackage("..validation..", "..validation")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..services..", "..services")
                    .as("Validation must not depend on service layer");

    @ArchTest
    static final ArchRule orderDoesNotDependOnSpecificModuleService =
            noClasses().that().resideInAnyPackage("..order..", "..order")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..payment..", "..payment", "..notification", "..notification", "..customer..", "..customer")
                    .as("Order module must not depend on specific module services");

    @ArchTest
    static final ArchRule notificationDoesNotDependOnOtherSubmodules =
            noClasses().that().resideInAnyPackage("..notification..", "..notification")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..order..", "..order", "..payment..", "..payment", "..customer..", "..customer")
                    .as("Notification module must not depend on specific module services");

    @ArchTest
    static final ArchRule paymentDoesNotDependOnOtherSubmodules =
            noClasses().that().resideInAnyPackage("..payment..", "..payment")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..order..", "..order", "..notification..", "..notification", "..customer..", "..customer")
                    .as("Payment module must not depend on specific module services");

    @ArchTest
    static final ArchRule customerDoesNotDependOnOtherSubmodules =
            noClasses().that().resideInAnyPackage("..customer..", "..customer")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..order..", "..order", "..notification..", "..notification", "..payment..", "..payment")
                    .as("Customer module must not depend on specific module services");

}

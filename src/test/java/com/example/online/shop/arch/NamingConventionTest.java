package com.example.online.shop.arch;

import com.example.online.shop.OrderApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packagesOf = OrderApplication.class,
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        }
)
public class NamingConventionTest {

    @ArchTest
    static final ArchRule controllersShouldBeNamedCorrectly =
            classes().that().resideInAPackage("..api")
                    .and().areAnnotatedWith(RestController.class)
                    .should().haveSimpleNameEndingWith("Controller")
                    .as("Controllers must end with 'Controller'");

    @ArchTest
    static final ArchRule servicesShouldBeNamedCorrectly =
            classes().that().resideInAPackage("..service")
                    .and().areAnnotatedWith(Service.class)
                    .should().haveSimpleNameEndingWith("Service")
                    .as("Services must end with 'Service'");

    @ArchTest
    static final ArchRule repositoriesShouldBeNamedCorrectly =
            classes().that().resideInAPackage("..repository")
                    .and().areAnnotatedWith(Repository.class)
                    .should().haveSimpleNameEndingWith("Repository")
                    .as("Repositories must end with 'Repository'");
}

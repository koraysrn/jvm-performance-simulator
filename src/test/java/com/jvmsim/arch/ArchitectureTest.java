package com.jvmsim.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SDD guardrails: these tests fail the build when any AI-generated or hand-written code
 * violates the architectural boundaries defined in CLAUDE.md and spec.md.
 */
@AnalyzeClasses(packages = "com.jvmsim", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule model_depends_only_on_java = classes()
            .that().resideInAPackage("..model..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..model..");

    @ArchTest
    static final ArchRule io_and_parse_stay_away_from_business_logic = classes()
            .that().resideInAnyPackage("..io..", "..parse..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "..model..",
                    "..io..",
                    "..parse..",
                    "org.apache.commons.csv..");

    @ArchTest
    static final ArchRule process_depends_only_on_model = classes()
            .that().resideInAPackage("..process..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..model..", "..process..");

    @ArchTest
    static final ArchRule output_depends_only_on_model = classes()
            .that().resideInAPackage("..output..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..model..", "..output..");

    @ArchTest
    static final ArchRule every_model_class_is_a_record = classes()
            .that().resideInAPackage("..model..")
            .should().beRecords();

    @ArchTest
    static final ArchRule no_traditional_thread_pools = noClasses()
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("java.util.concurrent.Executors")
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("java.util.concurrent.ThreadPoolExecutor");
}

package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate.ShellyConsumptionsHourlyAggregatorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture test to ensure that all classes annotated with @Service also have @Transactional annotation.
 * 
 * <p>This test enforces the architectural rule that all service classes should be transactional by default.
 * This helps prevent issues with transaction management and ensures consistent behavior across the application.</p>
 * 
 * <p>There are cases where services should not be transactional, such as:</p>
 * <ul>
 *   <li>Services that call external APIs where transactions don't make sense</li>
 *   <li>Services that need custom transaction behavior</li>
 * </ul>
 * 
 * <p>For these cases, you can add the class to the EXCEPTIONS set in the static initializer block.</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * // To add an exception programmatically:
 * ServiceTransactionalArchitectureTest.addException("YourServiceClassName");
 * 
 * // Or add it directly to the EXCEPTIONS set in the static initializer block:
 * static {
 *     EXCEPTIONS.add("YourServiceClassName");
 * }
 * </pre>
 */
public class ServiceTransactionalArchTest extends BaseArchTest {

    // Set of classes that are exempt from the rule
    private static final Set<String> EXCEPTIONS = new HashSet<>();

    static {
        // Add classes that are exempt from the @Transactional requirement
        // For example, services that don't modify data or have custom transaction handling
        addException(DatadisSyncService.class.getSimpleName());
        addException(SyncDailyPricesService.class.getSimpleName());
        addException(SyncHuaweiProductionService.class.getSimpleName());
        addException(ShellyConsumptionsHourlyAggregatorService.class.getSimpleName());
        addException(DatadisMonthlyAggregationService.class.getSimpleName());
        addException(DatadisYearlyAggregationService.class.getSimpleName());
        addException(DatadisProductionMonthlyAggregationService.class.getSimpleName());
        addException(DatadisProductionYearlyAggregationService.class.getSimpleName());
        addException(HuaweiProductionMonthlyAggregationService.class.getSimpleName());
        addException(HuaweiProductionYearlyAggregationService.class.getSimpleName());
        addException(CommunityAccessGuard.class.getSimpleName());
        // Add more exceptions as needed
    }

    @Test
    void servicesAreTransactional() {

        ArchRule rule;

        if (EXCEPTIONS.isEmpty()) {
            rule = classes()
                    .that().areAnnotatedWith(Service.class)
                    .should(beAnnotatedWithTransactional());
        } else {
            rule = classes()
                    .that().areAnnotatedWith(Service.class)
                    .and().haveNameNotMatching(".*(" + String.join("|", EXCEPTIONS) + ").*")
                    .should(beAnnotatedWithTransactional());
        }

        rule.check(IMPORTED_CLASSES);
    }

    private ArchCondition<com.tngtech.archunit.core.domain.JavaClass> beAnnotatedWithTransactional() {
        return new ArchCondition<>("be annotated with @Transactional") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
                boolean hasTransactional = javaClass.isAnnotatedWith(Transactional.class);

                if (!hasTransactional) {
                    String message = String.format(
                            "Class %s is annotated with @Service but not with @Transactional",
                            javaClass.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }

    /**
     * Add a class to the exception list.
     * This method is provided for convenience to add exceptions programmatically.
     * 
     * @param className the fully qualified name of the class to exempt
     */
    public static void addException(String className) {
        EXCEPTIONS.add(className);
    }
}

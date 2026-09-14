package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared ArchUnit condition used by {@link RepositoryTransactionalArchTest} and
 * {@link ServiceTransactionalArchTest} to reject classes that mix read-only and write
 * transactional methods: a class-level {@code @Transactional} default that is quietly flipped
 * per-method purely to toggle {@code readOnly}, instead of being split into separate classes.
 *
 * <p>A method-level {@code @Transactional} is only legitimate when it configures something other
 * than {@code readOnly} (propagation, isolation, timeout, rollback rules, etc.).</p>
 */
final class TransactionalMixingCondition {

    private TransactionalMixingCondition() {
    }

    static ArchCondition<JavaClass> notMixReadOnlyAndWriteMethods() {
        return new ArchCondition<>("not mix read-only and write @Transactional methods") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    if (!method.isAnnotatedWith(Transactional.class)) {
                        continue;
                    }
                    Transactional annotation = method.getAnnotationOfType(Transactional.class);
                    if (isReadOnlyOnlyOverride(annotation)) {
                        String message = String.format(
                                "Method %s.%s overrides @Transactional only to toggle readOnly; " +
                                        "the class-level annotation should be the single source of truth. " +
                                        "Split the class by responsibility instead of mixing modes.",
                                javaClass.getSimpleName(), method.getName());
                        events.add(SimpleConditionEvent.violated(method, message));
                    }
                }
            }
        };
    }

    private static boolean isReadOnlyOnlyOverride(Transactional annotation) {
        return annotation.value().isEmpty()
                && annotation.transactionManager().isEmpty()
                && annotation.label().length == 0
                && annotation.propagation() == Propagation.REQUIRED
                && annotation.isolation() == Isolation.DEFAULT
                && annotation.timeout() == -1
                && annotation.timeoutString().isEmpty()
                && annotation.rollbackFor().length == 0
                && annotation.rollbackForClassName().length == 0
                && annotation.noRollbackFor().length == 0
                && annotation.noRollbackForClassName().length == 0;
    }
}

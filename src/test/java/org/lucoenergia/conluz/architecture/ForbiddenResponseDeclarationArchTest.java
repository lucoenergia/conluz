package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.security.PasswordChangeAllowedEndpoints;
import org.lucoenergia.conluz.infrastructure.shared.security.PublicEndpoints;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.PasswordChangeRequiredErrorResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Architecture test enforcing that every endpoint that can refuse a user who must change their password (#342)
 * documents that 403, and that the endpoints that never refuse them do not. It is documented by
 * {@link ForbiddenErrorResponse}, together with an authorization refusal, or by
 * {@link PasswordChangeRequiredErrorResponse} alone, where the authorization never answers 403; never by both.
 *
 * <p>The refusal comes from a filter, not from the endpoint, so nothing else would make a new endpoint declare it.
 * Which endpoints are exempt is read from {@link PasswordChangeAllowedEndpoints} itself, by matching each handler's
 * mapped requests against it, so the exempt endpoints are never listed twice.</p>
 */
public class ForbiddenResponseDeclarationArchTest extends BaseArchTest {

    @Test
    void everyEndpointThatCanRefuseAUserWhoMustChangeTheirPassword_declaresThe403() {
        methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .or().areDeclaredInClassesThat().areAnnotatedWith(Controller.class)
                .should(declareThe403ExactlyWhenTheFilterCanRefuse())
                .because("the 403 USER_PASSWORD_CHANGE_REQUIRED is answered by a filter in front of every "
                        + "authenticated endpoint except the ones a user who must change their password may call")
                .check(IMPORTED_CLASSES);
    }

    private static ArchCondition<JavaMethod> declareThe403ExactlyWhenTheFilterCanRefuse() {
        return new ArchCondition<>("declare their 403 exactly when the filter can refuse them") {
            @Override
            public void check(JavaMethod javaMethod, ConditionEvents events) {
                Method method = javaMethod.reflect();
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    return;
                }
                List<MockHttpServletRequest> requests = requestsMappedBy(method, mapping);
                String id = javaMethod.getOwner().getSimpleName() + "#" + javaMethod.getName();
                boolean withAuthorization = javaMethod.isAnnotatedWith(ForbiddenErrorResponse.class);
                boolean alone = javaMethod.isAnnotatedWith(PasswordChangeRequiredErrorResponse.class);

                if (withAuthorization && alone) {
                    events.add(SimpleConditionEvent.violated(javaMethod, id + " declares both "
                            + "@ForbiddenErrorResponse and @PasswordChangeRequiredErrorResponse"));
                } else if (requests.stream().anyMatch(PasswordChangeAllowedEndpoints.MATCHER::matches)) {
                    events.add(new SimpleConditionEvent(javaMethod, !withAuthorization && !alone, id + " is always "
                            + "allowed to a user who must change their password, but declares a 403 for them"));
                } else if (!requests.stream().allMatch(PublicEndpoints.MATCHER::matches)) {
                    events.add(new SimpleConditionEvent(javaMethod, withAuthorization || alone, id + " refuses a "
                            + "user who must change their password with 403, but declares neither "
                            + "@ForbiddenErrorResponse nor @PasswordChangeRequiredErrorResponse"));
                }
            }
        };
    }

    /**
     * One request per HTTP method and path the handler maps, with every path variable replaced by a sample value.
     */
    private static List<MockHttpServletRequest> requestsMappedBy(Method method, RequestMapping mapping) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(),
                RequestMapping.class);
        List<String> prefixes = classMapping == null || classMapping.path().length == 0
                ? List.of("") : List.of(classMapping.path());
        List<String> suffixes = mapping.path().length == 0 ? List.of("") : List.of(mapping.path());
        RequestMethod[] httpMethods = mapping.method().length == 0 ? RequestMethod.values() : mapping.method();

        List<MockHttpServletRequest> requests = new ArrayList<>();
        for (String prefix : prefixes) {
            for (String suffix : suffixes) {
                String path = (prefix + suffix).replaceAll("\\{[^}]+}", "sample");
                for (RequestMethod httpMethod : httpMethods) {
                    requests.add(new MockHttpServletRequest(httpMethod.name(), path));
                }
            }
        }
        return requests;
    }
}

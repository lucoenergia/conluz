package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.architecture.CapabilityInventory.Capability;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@link CapabilityInventory} honest in both directions.
 *
 * <p>An endpoint's authorization decision is something a client has to know before it can decide
 * whether to offer the action. The point of these rules is that adding one cannot be forgotten: a
 * new guard method, or a new {@code @PreAuthorize} using one, fails the build until the inventory
 * says which capability reports it — or states why nothing does.</p>
 *
 * <p>The reverse rules matter just as much. A capability field that no guard maps to is a promise
 * the server never checks, and an inventory entry naming a field that does not exist is a rule
 * nobody reports. Both would be invisible without a test.</p>
 */
class CapabilityCoverageArchTest extends BaseArchTest {

    private static final Pattern GUARD_CALL = Pattern.compile("@communityAccessGuard\\.(\\w+)\\(");

    private static final Map<String, String> CAPABILITY_CLASSES = Map.of(
            CapabilityInventory.PLATFORM, "PlatformCapabilitiesResponse",
            CapabilityInventory.COMMUNITY, "CommunityCapabilitiesResponse",
            CapabilityInventory.SUPPLY, "SupplyCapabilitiesResponse",
            CapabilityInventory.PLANT, "PlantCapabilitiesResponse",
            CapabilityInventory.SHARING_AGREEMENT, "SharingAgreementCapabilitiesResponse",
            CapabilityInventory.USER, "UserCapabilitiesResponse",
            CapabilityInventory.MEMBERSHIP, "MembershipCapabilitiesResponse");

    private static final String CAPABILITY_PACKAGE =
            "org.lucoenergia.conluz.infrastructure.admin.community.access.capability.";

    /**
     * The rule the epic asks for: a new endpoint whose guard nobody has classified breaks the build.
     */
    @Test
    void everyGuardUsedInAPreAuthorizeIsInTheInventory() {
        Set<String> used = new TreeSet<>();
        for (JavaClass javaClass : IMPORTED_CLASSES) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (!method.isAnnotatedWith(PreAuthorize.class)) {
                    continue;
                }
                Matcher matcher = GUARD_CALL.matcher(method.getAnnotationOfType(PreAuthorize.class).value());
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }
            }
        }

        assertTrue(!used.isEmpty(), "no @PreAuthorize guard calls were found at all -- the rule is inert");
        Set<String> unclassified = new TreeSet<>(used);
        unclassified.removeAll(CapabilityInventory.knownGuardMethods());
        assertTrue(unclassified.isEmpty(),
                () -> "these guards are used by an endpoint but are absent from CapabilityInventory: "
                        + unclassified + ". Map each to the capability that reports it, or record it "
                        + "as server-only with the reason nothing does.");
    }

    /**
     * Catches a guard method added to the interface but not yet used by an endpoint. Waiting for the
     * @PreAuthorize would mean the decision arrives already unclassified.
     */
    @Test
    void everyGuardMethodDeclaredOnTheInterfaceIsInTheInventory() {
        Set<String> declared = new TreeSet<>();
        collectDeclaredMethods(CommunityAccessGuard.class, declared);

        Set<String> unclassified = new TreeSet<>(declared);
        unclassified.removeAll(CapabilityInventory.knownGuardMethods());
        assertTrue(unclassified.isEmpty(),
                () -> "these guard methods exist but are absent from CapabilityInventory: " + unclassified);
    }

    /**
     * Nothing stale: an inventory entry for a guard that no longer exists is a rule nobody can
     * report, and would sit there looking like coverage.
     */
    @Test
    void theInventoryNamesNoGuardThatHasBeenRemoved() {
        Set<String> declared = new TreeSet<>();
        collectDeclaredMethods(CommunityAccessGuard.class, declared);

        Set<String> phantom = new TreeSet<>(CapabilityInventory.knownGuardMethods());
        phantom.removeAll(declared);
        assertTrue(phantom.isEmpty(),
                () -> "CapabilityInventory names guards that no longer exist: " + phantom);
    }

    /**
     * Every boolean a client can read is backed by a rule the server actually applies. A field with
     * no guard behind it is a promise nothing checks — the one deliberate exception is recorded in
     * the inventory rather than tolerated here.
     */
    @Test
    void everyCapabilityFieldIsAccountedForByTheInventory() throws Exception {
        Set<Capability> declared = declaredCapabilityFields();
        Set<Capability> accounted = CapabilityInventory.allCapabilities();

        Set<Capability> unaccounted = new LinkedHashSet<>(declared);
        unaccounted.removeAll(accounted);
        assertTrue(unaccounted.isEmpty(),
                () -> "these capability fields are returned to clients but no guard in the inventory "
                        + "reports them: " + unaccounted);
    }

    /**
     * And the other way: an inventory entry naming a field that no schema declares would claim
     * coverage that cannot reach a client — a renamed field would otherwise pass unnoticed.
     */
    @Test
    void theInventoryNamesNoCapabilityFieldThatDoesNotExist() throws Exception {
        Set<Capability> declared = declaredCapabilityFields();

        Set<Capability> phantom = new LinkedHashSet<>(CapabilityInventory.allCapabilities());
        phantom.removeAll(declared);
        assertTrue(phantom.isEmpty(),
                () -> "CapabilityInventory names capability fields that no response declares: " + phantom);
    }

    /**
     * Every resource named in the inventory resolves to a schema, so a typo in a resource key cannot
     * silently exclude a whole response from the two rules above.
     */
    @Test
    void everyResourceInTheInventoryHasACapabilitySchema() {
        Set<String> resources = new TreeSet<>();
        CapabilityInventory.allCapabilities().forEach(capability -> resources.add(capability.resource()));

        assertEquals(Set.of(), difference(resources, CAPABILITY_CLASSES.keySet()),
                "the inventory names resources with no capability schema");
    }

    private Set<Capability> declaredCapabilityFields() throws ClassNotFoundException {
        Set<Capability> declared = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : CAPABILITY_CLASSES.entrySet()) {
            Class<?> type = Class.forName(CAPABILITY_PACKAGE + entry.getValue());
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == boolean.class) {
                    declared.add(new Capability(entry.getKey(), field.getName()));
                }
            }
        }
        return declared;
    }

    private void collectDeclaredMethods(Class<?> type, Set<String> names) {
        for (Method method : type.getDeclaredMethods()) {
            names.add(method.getName());
        }
        for (Class<?> parent : type.getInterfaces()) {
            collectDeclaredMethods(parent, names);
        }
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> difference = new TreeSet<>(left);
        difference.removeAll(right);
        return difference;
    }
}

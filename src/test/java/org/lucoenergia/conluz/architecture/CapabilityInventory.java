package org.lucoenergia.conluz.architecture;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which capability each guard method is reported as, and which are deliberately not reported at all.
 *
 * <p>This is a test oracle: nothing in production reads it, and putting it in {@code src/main} would
 * ship a class with no runtime consumer. What it buys is that a new endpoint cannot quietly arrive
 * without somebody deciding whether its decision belongs in a response — {@link CapabilityCoverageArchTest}
 * fails the build until this file names the guard.</p>
 *
 * <p>Keyed by method <em>name</em>, not signature, because that is what a {@code @PreAuthorize}
 * expression carries. One name can map to several capabilities: {@code canReadSupply} is both the
 * supply's own {@code canRead} and the plant's {@code canReadSupply}, and
 * {@code canManageSharingAgreement} is two overloads reported on two different resources.</p>
 */
final class CapabilityInventory {

    private CapabilityInventory() {
    }

    /**
     * A capability as a client sees it: which resource's {@code capabilities} object carries it, and
     * under what name.
     */
    record Capability(String resource, String field) {
        @Override
        public String toString() {
            return resource + "." + field;
        }
    }

    static final String PLATFORM = "platform";
    static final String COMMUNITY = "community";
    static final String SUPPLY = "supply";
    static final String PLANT = "plant";
    static final String SHARING_AGREEMENT = "sharingAgreement";
    static final String USER = "user";
    static final String MEMBERSHIP = "membership";

    private static final Map<String, Set<Capability>> REPORTED = new LinkedHashMap<>();
    private static final Map<String, String> SERVER_ONLY = new LinkedHashMap<>();
    private static final Set<Capability> WITHOUT_GUARD = new LinkedHashSet<>();

    static {
        // --- platform ---
        report("canCreateCommunity", capability(PLATFORM, "canCreateCommunity"));
        report("canListUsers", capability(PLATFORM, "canListUsers"));

        // --- community ---
        report("canReadCommunity", capability(COMMUNITY, "canRead"));
        report("canUpdateCommunity", capability(COMMUNITY, "canUpdate"));
        report("canEnableCommunity", capability(COMMUNITY, "canEnable"));
        report("canDisableCommunity", capability(COMMUNITY, "canDisable"));
        report("canManageCommunity", capability(COMMUNITY, "canManage"));
        report("canReadCommunityProduction", capability(COMMUNITY, "canReadProduction"));
        report("canListSupplies", capability(COMMUNITY, "canListSupplies"));
        report("canListPlants", capability(COMMUNITY, "canListPlants"));
        report("canCreateUserIn", capability(COMMUNITY, "canCreateUsers"));

        // Community-wide rules that a membership also reports, because they are what a client needs
        // to decide whether to offer the control on one row of the roster.
        report("canManageMemberships",
                capability(COMMUNITY, "canManageMemberships"),
                capability(MEMBERSHIP, "canUpdateRole"),
                capability(MEMBERSHIP, "canDelete"));
        report("canManageMembershipInvestment",
                capability(COMMUNITY, "canManageMembershipInvestment"),
                capability(MEMBERSHIP, "canManageInvestment"));
        report("canReadMembershipPayback", capability(MEMBERSHIP, "canReadPayback"));

        // --- supply ---
        // Also the plant's canReadSupply: PlantResponse.supply is a reference carrying no owner, so
        // the plant reports whether following it would succeed.
        report("canReadSupply", capability(SUPPLY, "canRead"), capability(PLANT, "canReadSupply"));
        report("canEditSupply", capability(SUPPLY, "canEdit"));
        report("canReadSupplyPartitionCoefficients", capability(SUPPLY, "canReadPartitionCoefficients"));
        report("canCreatePlant", capability(SUPPLY, "canCreatePlant"));

        // --- plant ---
        report("canReadPlant", capability(PLANT, "canRead"));
        report("canManagePlant", capability(PLANT, "canManage"));
        report("canListSharingAgreements", capability(PLANT, "canListSharingAgreements"));
        // Two overloads share this name. The one-argument form asks about the plant (may the caller
        // create an agreement under it); the two-argument form asks about one agreement.
        report("canManageSharingAgreement",
                capability(PLANT, "canManageSharingAgreements"),
                capability(SHARING_AGREEMENT, "canManage"));
        report("canReadSharingAgreement", capability(SHARING_AGREEMENT, "canRead"));

        // --- user ---
        report("canReadUser", capability(USER, "canRead"));
        report("canEditUser", capability(USER, "canEdit"));
        report("canDeleteUser", capability(USER, "canDelete"));
        report("canEnableUser", capability(USER, "canEnable"));
        report("canDisableUser", capability(USER, "canDisable"));
        report("canGrantPlatformAdmin", capability(USER, "canGrantPlatformAdmin"));
        report("canRevokePlatformAdmin", capability(USER, "canRevokePlatformAdmin"));
        report("canListSuppliesOfUser", capability(USER, "canListSupplies"));

        // --- reported to nobody ---
        serverOnly("isCommunityAdminOfSupply",
                "Shapes a response body rather than gating a request: the partition-coefficient "
                        + "history passes it as includePending. A client that knew it would learn "
                        + "nothing it cannot see in the response itself.");
        serverOnly("isMemberOfCommunity",
                "The rule behind canReadCommunityProduction and canListSupplies, which are what the "
                        + "endpoints use and what the community reports. Naming it again would report "
                        + "one decision twice.");
        serverOnly("visibleCommunityIds",
                "A scope for a query, not a decision about an object. The listing it scopes reports "
                        + "its own capabilities on each item it returns.");
        serverOnly("adminCommunityIds",
                "As visibleCommunityIds: it chooses which rows a listing loads, and each row then "
                        + "carries its own capabilities.");
        serverOnly("isCurrentUser",
                "A fact the client already has -- it knows who it is. The rules that care about it "
                        + "(canDeleteUser and friends) fold it in and are reported themselves.");

        // --- capabilities with no guard of their own ---
        // Creating a plant always names a supply, so no endpoint asks this question community-wide.
        // The community reports it anyway, because a client has to decide whether to offer the
        // action before any supply is chosen.
        withoutGuard(capability(COMMUNITY, "canCreatePlants"));
    }

    static Map<String, Set<Capability>> reported() {
        return Map.copyOf(REPORTED);
    }

    static Map<String, String> serverOnly() {
        return Map.copyOf(SERVER_ONLY);
    }

    static Set<Capability> withoutGuard() {
        return Set.copyOf(WITHOUT_GUARD);
    }

    static Set<String> knownGuardMethods() {
        Set<String> names = new LinkedHashSet<>(REPORTED.keySet());
        names.addAll(SERVER_ONLY.keySet());
        return names;
    }

    /** Every capability this inventory claims exists, wherever it comes from. */
    static Set<Capability> allCapabilities() {
        Set<Capability> all = new LinkedHashSet<>(WITHOUT_GUARD);
        REPORTED.values().forEach(all::addAll);
        return all;
    }

    private static Capability capability(String resource, String field) {
        return new Capability(resource, field);
    }

    private static void report(String guardMethod, Capability... capabilities) {
        REPORTED.put(guardMethod, new LinkedHashSet<>(Set.of(capabilities)));
    }

    private static void serverOnly(String guardMethod, String reason) {
        SERVER_ONLY.put(guardMethod, reason);
    }

    private static void withoutGuard(Capability capability) {
        WITHOUT_GUARD.add(capability);
    }
}

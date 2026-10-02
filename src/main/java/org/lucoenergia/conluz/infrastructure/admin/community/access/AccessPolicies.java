package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.policy.CommunityAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.MembershipAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.PlantAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.PlatformAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.SharingAgreementAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.SupplyAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.UserAccessPolicy;
import org.springframework.stereotype.Component;

/**
 * The one set of access policies the application uses, so a guard and a capability assembler asking
 * the same question are running the same code rather than two copies of it that can drift apart.
 *
 * <p>A policy cannot be a Spring bean itself — {@code AccessPolicyPurityArchTest} forbids anything
 * in {@code ..access.policy..} from depending on Spring, which is what keeps a rule evaluable
 * outside a request. So the wiring lives here instead: this class knows about Spring, the policies
 * it holds do not. Policies are stateless, so one instance each is enough.</p>
 *
 * <p>Deliberately a holder rather than seven {@code @Bean} methods: {@link CommunityAccessGuardImpl}
 * already takes six repositories, and injecting every policy separately would give it a
 * thirteen-argument constructor.</p>
 */
@Component
public class AccessPolicies {

    private final CommunityAccessPolicy community = new CommunityAccessPolicy();
    private final MembershipAccessPolicy membership = new MembershipAccessPolicy();
    private final PlatformAccessPolicy platform = new PlatformAccessPolicy();
    private final SupplyAccessPolicy supply = new SupplyAccessPolicy();
    private final UserAccessPolicy user = new UserAccessPolicy();
    private final PlantAccessPolicy plant = new PlantAccessPolicy(supply);
    private final SharingAgreementAccessPolicy sharingAgreement = new SharingAgreementAccessPolicy(plant);

    public CommunityAccessPolicy community() {
        return community;
    }

    public MembershipAccessPolicy membership() {
        return membership;
    }

    public PlatformAccessPolicy platform() {
        return platform;
    }

    public SupplyAccessPolicy supply() {
        return supply;
    }

    public UserAccessPolicy user() {
        return user;
    }

    public PlantAccessPolicy plant() {
        return plant;
    }

    public SharingAgreementAccessPolicy sharingAgreement() {
        return sharingAgreement;
    }
}

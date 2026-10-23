package org.lucoenergia.conluz.infrastructure.admin.community.admins.notification;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.AdminChangeActor;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.LastActiveCommunityAdminRule;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds every notification of a change to a community's admins (#466, MEM-035), inside the transaction of the
 * change: it reads the recipients there, in one read of the community's admin memberships with their accounts, and
 * hands the emails to the sender, which sends them only after the commit. The event is logged after the commit too,
 * so a change that rolls back leaves neither an email nor a line.
 *
 * <p>Everything but the read of the recipients is isolated from the change: a failure to build or hand over an
 * email is caught and logged. A database failure of that read cannot be isolated, as PostgreSQL aborts the whole
 * transaction on a failed statement; it is the same read the operation's own rows already went through, so it fails
 * only when the database does, and the change could not commit then anyway.</p>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CommunityAdminChangeNotifierImpl implements CommunityAdminChangeNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityAdminChangeNotifierImpl.class);

    /** The changes after which the affected admin is told too, when they lose or regain real access (#466). */
    private static final Set<CommunityAdminChange> LOSING_ACCESS = Set.of(CommunityAdminChange.DEMOTED,
            CommunityAdminChange.REMOVED, CommunityAdminChange.SUSPENDED);

    private final GetMembershipsRepository getMembershipsRepository;
    private final CommunityAdminChangeEmailFactory emailFactory;
    private final EmailSender emailSender;
    private final Clock clock;

    public CommunityAdminChangeNotifierImpl(GetMembershipsRepository getMembershipsRepository,
                                            CommunityAdminChangeEmailFactory emailFactory, EmailSender emailSender,
                                            Clock clock) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.emailFactory = emailFactory;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Override
    public void notify(CommunityAdminChange change, CommunityMembership before, CommunityMembership after,
                       AdminChangeActor actor) {
        CommunityMembership affected = after != null ? after : before;
        UUID communityId = affected.getCommunity().getId();
        try {
            if (!touchesAnAdmin(before, after) || !changed(change, before, after)) {
                return;
            }
            UUID affectedId = affected.getUser().getId();
            UUID actorId = actor.userId().getId();
            List<CommunityMembership> admins = getMembershipsRepository.findByCommunityIdAndRole(communityId,
                    CommunityRole.COMMUNITY_ADMIN);

            CommunityAdminChangeEmailFactory.Message message = new CommunityAdminChangeEmailFactory.Message(change,
                    affected.getCommunity().getName(), affected.getFullName(),
                    actorName(actor, affected, admins), actorId.equals(affectedId), clock.instant());

            List<Email> emails = new ArrayList<>();
            for (CommunityMembership admin : admins) {
                UUID adminId = admin.getUser().getId();
                if (LastActiveCommunityAdminRule.countsAsActiveAdmin(admin) && admin.getContactEmail() != null
                        && !adminId.equals(actorId) && !adminId.equals(affectedId)) {
                    emails.add(emailFactory.build(admin.getContactEmail(), message, false));
                }
            }
            if (affectedIsTold(change, before, after) && affected.getContactEmail() != null
                    && !affectedId.equals(actorId)) {
                emails.add(emailFactory.build(affected.getContactEmail(), message, true));
            }

            emails.forEach(emailSender::send);
            logAfterCommit(communityId, affectedId, change, actorId, emails.size());
        } catch (RuntimeException e) {
            LOGGER.warn("Admin change {} in community {} not notified: {}", change, communityId,
                    e.getClass().getSimpleName());
        }
    }

    /**
     * The same services change members' memberships too: deleting a member, or a member accepting an invitation, is
     * not a change to the community's admins.
     */
    private static boolean touchesAnAdmin(CommunityMembership before, CommunityMembership after) {
        return (before != null && before.getRole() == CommunityRole.COMMUNITY_ADMIN)
                || (after != null && after.getRole() == CommunityRole.COMMUNITY_ADMIN);
    }

    /**
     * A request that leaves the membership as it was, such as giving a membership the role it already has or
     * enabling an enabled one, changed nothing to tell anyone about.
     */
    private static boolean changed(CommunityAdminChange change, CommunityMembership before,
                                   CommunityMembership after) {
        return switch (change) {
            case PROMOTED, DEMOTED -> before.getRole() != after.getRole();
            case SUSPENDED, REACTIVATED -> before.isDisabled() != after.isDisabled();
            case INVITED, INVITATION_RESENT, ACCEPTED, REMOVED -> true;
        };
    }

    /**
     * The affected admin is told only when they lose or regain real access, by the predicate that decides who counts
     * as an active admin: a pending admin whose membership is deleted never had it.
     */
    private static boolean affectedIsTold(CommunityAdminChange change, CommunityMembership before,
                                          CommunityMembership after) {
        if (LOSING_ACCESS.contains(change)) {
            return LastActiveCommunityAdminRule.countsAsActiveAdmin(before);
        }
        return change == CommunityAdminChange.REACTIVATED && LastActiveCommunityAdminRule.countsAsActiveAdmin(after);
    }

    /**
     * Null names the platform operator. A person is named from their member record in the community: the affected
     * one's when they made the change themselves, or the actor's admin membership, read once in case it is no longer
     * one, demoted by another admin since the request was authorized.
     */
    private String actorName(AdminChangeActor actor, CommunityMembership affected,
                             List<CommunityMembership> admins) {
        if (actor.platformOperator()) {
            return null;
        }
        UUID actorId = actor.userId().getId();
        if (actorId.equals(affected.getUser().getId())) {
            return affected.getFullName();
        }
        Optional<CommunityMembership> actorMembership = admins.stream()
                .filter(admin -> admin.getUser().getId().equals(actorId))
                .findFirst()
                .or(() -> getMembershipsRepository.findByUserIdAndCommunityId(actorId,
                        affected.getCommunity().getId()));
        return actorMembership.map(CommunityMembership::getFullName).orElseThrow();
    }

    private static void logAfterCommit(UUID communityId, UUID affectedId, CommunityAdminChange change, UUID actorId,
                                       int emails) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                LOGGER.info("Admin change notified: communityId={}, userId={}, change={}, actorId={}, emails={}",
                        communityId, affectedId, change, actorId, emails);
            }
        });
    }
}

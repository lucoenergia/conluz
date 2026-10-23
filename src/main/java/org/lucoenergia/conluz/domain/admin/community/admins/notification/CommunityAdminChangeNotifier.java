package org.lucoenergia.conluz.domain.admin.community.admins.notification;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

/**
 * Tells a community's admins about a change to its admins (#466, MEM-035): the one place every such notification is
 * built. Each service that changes a community's admins calls it once, inside its transaction, after its own checks
 * have passed and its writes are done.
 *
 * <p>The recipients are the community's other active admins with a contact email, as
 * {@link org.lucoenergia.conluz.domain.admin.community.membership.LastActiveCommunityAdminRule#countsAsActiveAdmin}
 * decides, and the affected admin when the change makes them lose or regain access; never the actor. The emails are
 * handed to the sender, which sends them only after the transaction commits, and the event is logged then too.</p>
 *
 * <p>Nothing is sent or logged for a change that touches no admin membership, or that changed nothing.</p>
 */
public interface CommunityAdminChangeNotifier {

    /**
     * Never throws: a notification never changes the outcome of the operation. A failure to build or hand over the
     * emails is logged, without personal data.
     *
     * @param before the membership as it was before the change, read under the operation's locks; null for an
     *               invitation, which creates it
     * @param after  the membership as the change left it; null for a removal, which deletes it
     */
    void notify(CommunityAdminChange change, CommunityMembership before, CommunityMembership after,
                AdminChangeActor actor);
}

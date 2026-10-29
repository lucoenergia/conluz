package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

/**
 * A selection of memberships to invite is refused as a whole, before any of them is invited.
 */
public class InvitationSelectionRefusedException extends RuntimeException {

    private final InvitationSelectionProblem problem;

    public InvitationSelectionRefusedException(InvitationSelectionProblem problem) {
        super(problem.name());
        this.problem = problem;
    }

    public InvitationSelectionProblem getProblem() {
        return problem;
    }
}

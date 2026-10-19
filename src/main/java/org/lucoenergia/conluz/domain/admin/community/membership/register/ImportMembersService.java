package org.lucoenergia.conluz.domain.admin.community.membership.register;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Registers the members listed in a CSV file, each one as {@link RegisterMemberService} does, without sending
 * anything (ADR-0003 §5). The whole file is read and checked before any row is registered; then each row is
 * registered in a transaction of its own, so a row that fails keeps the rows registered before and after it.
 *
 * <p>A row's outcome is the same whether or not the person already had an account, and carries nothing the file did
 * not hold.</p>
 */
public interface ImportMembersService {

    /**
     * The most data rows one file may hold, so that one request stays bounded.
     */
    int MAX_ROWS = 1000;

    /**
     * @param csv the file's content: UTF-8, optionally with a byte order mark, or Windows-1252, separated by
     *            {@code ,} or {@code ;}
     * @return the outcome of each data row, in the order of the file
     * @throws MemberImportFileRefusedException if the file as a whole cannot be imported. Checked before any row is
     *         registered, so nothing is registered
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     */
    ImportedMembers importMembers(UUID communityId, UserId importedBy, byte[] csv);
}

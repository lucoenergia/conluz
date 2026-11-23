package org.lucoenergia.conluz.domain.admin.user.password;

/**
 * The passwords known to be compromised: common ones, and ones that have appeared in data breaches. The
 * {@link PasswordPolicy} refuses any of them.
 */
public interface PasswordBlocklist {

    /**
     * @param lowercasedPassword the candidate password, lowercased with {@link java.util.Locale#ROOT}: the list is
     *                           compared case-insensitively, and holds only lowercase entries
     * @return whether the password is in the list
     */
    boolean contains(String lowercasedPassword);
}

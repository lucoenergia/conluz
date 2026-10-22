package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.Map;

/**
 * One way a submitted list of contact channels breaks CTC-003.
 *
 * @param index  the position of the offending channel in the submitted list, or {@code null} when the violation is
 *               about the list as a whole
 * @param params the limit that was broken, keyed by name ({@code maxLength}, {@code maxChannels}, {@code minDigits}); empty when there
 *               is none
 */
public record ContactChannelViolation(ContactChannelViolationCode code, Integer index, Map<String, String> params) {

    public ContactChannelViolation {
        params = Map.copyOf(params);
    }
}

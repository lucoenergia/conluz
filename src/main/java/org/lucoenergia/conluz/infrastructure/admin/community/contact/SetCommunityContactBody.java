package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelDraft;

import java.util.List;

@Schema(requiredProperties = {"channels"})
public class SetCommunityContactBody {

    @Schema(description = "Every contact channel of the community, in the order they are shown. Replaces the stored "
            + "list as a whole; an empty list removes every channel. At most 10.")
    private List<ContactChannelBody> channels;

    public SetCommunityContactBody() {
    }

    public SetCommunityContactBody(List<ContactChannelBody> channels) {
        this.channels = channels;
    }

    public List<ContactChannelBody> getChannels() {
        return channels;
    }

    public void setChannels(List<ContactChannelBody> channels) {
        this.channels = channels;
    }

    /**
     * @return the submitted channels in their order, or {@code null} if the list is missing
     */
    public List<ContactChannelDraft> toDrafts() {
        if (channels == null) {
            return null;
        }
        return channels.stream()
                .map(channel -> channel == null ? null : channel.toDraft())
                .toList();
    }
}

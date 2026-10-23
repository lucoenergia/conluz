package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;

import java.util.List;

@Schema(requiredProperties = {"channels"})
public class CommunityContactResponse {

    @Schema(description = "Every contact channel of the community, in the order they are shown. Empty if it has "
            + "none.")
    private final List<ContactChannelResponse> channels;

    private CommunityContactResponse(List<ContactChannelResponse> channels) {
        this.channels = channels;
    }

    public static CommunityContactResponse of(List<ContactChannel> channels) {
        return new CommunityContactResponse(channels.stream().map(ContactChannelResponse::of).toList());
    }

    public List<ContactChannelResponse> getChannels() {
        return channels;
    }
}

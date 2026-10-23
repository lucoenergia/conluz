package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommunityContactChannelEntityMapper {

    public ContactChannel map(CommunityContactChannelEntity entity) {
        return new ContactChannel(entity.getType(), entity.getLabel(), entity.getValue(), entity.getSchedule(),
                entity.getNotes());
    }

    public CommunityContactChannelEntity map(ContactChannel channel, CommunityEntity community, int position) {
        CommunityContactChannelEntity entity = new CommunityContactChannelEntity();
        entity.setId(UUID.randomUUID());
        entity.setCommunity(community);
        entity.setType(channel.getType());
        entity.setLabel(channel.getLabel());
        entity.setValue(channel.getValue());
        entity.setSchedule(channel.getSchedule());
        entity.setNotes(channel.getNotes());
        entity.setPosition(position);
        return entity;
    }
}

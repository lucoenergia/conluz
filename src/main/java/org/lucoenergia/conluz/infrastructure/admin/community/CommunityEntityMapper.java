package org.lucoenergia.conluz.infrastructure.admin.community;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
public class CommunityEntityMapper extends BaseMapper<CommunityEntity, Community> {

    @Override
    public Community map(CommunityEntity entity) {
        return new Community.Builder()
                .withId(entity.getId())
                .withName(entity.getName())
                .withCode(entity.getCode())
                .withLegalId(entity.getLegalId())
                .withAddress(entity.getAddress())
                .withEnabled(entity.isEnabled())
                .build();
    }
}

package org.lucoenergia.conluz.domain.admin.community.state;

import java.util.UUID;

public interface CommunityStateRepository {

    void enable(UUID id);

    void disable(UUID id);
}

package org.lucoenergia.conluz.domain.admin.community;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;

import java.util.UUID;

public class CommunityMother {

    public static Community.Builder random() {
        return new Community.Builder()
                .withId(UUID.randomUUID())
                .withName(RandomStringUtils.random(20, true, false))
                .withCode(RandomStringUtils.random(10, true, true))
                .withLegalId(RandomStringUtils.random(9, false, true))
                .withAddress(RandomStringUtils.random(30, true, false))
                .withEnabled(true);
    }

    public static CommunityEntity.Builder randomEntity() {
        return new CommunityEntity.Builder()
                .withId(UUID.randomUUID())
                .withName(RandomStringUtils.random(20, true, false))
                .withCode(RandomStringUtils.random(10, true, true))
                .withLegalId(RandomStringUtils.random(9, false, true))
                .withAddress(RandomStringUtils.random(30, true, false))
                .withEnabled(true);
    }
}

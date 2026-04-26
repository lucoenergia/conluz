package org.lucoenergia.conluz.infrastructure.shared.info;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetInfoService {

    private final BuildProperties buildProperties;

    public GetInfoService(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public String getVersion() {
        return buildProperties.getVersion();
    }
}

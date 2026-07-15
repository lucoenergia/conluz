package org.lucoenergia.conluz.domain.production.plant.distributorfile;

import java.util.Set;

/**
 * Parses and validates a distributor coefficient-partition file. Pure domain logic: no
 * persistence, no I/O beyond the bytes already in memory. The caller is responsible for
 * resolving {@code plantRegulatoryCode} and {@code knownCups} (community-scoped) before calling.
 */
public interface DistributorFileParser {

    /**
     * @param filename            original uploaded filename, expected shape {@code {code}_{YYYY}.txt}
     * @param content             raw file bytes
     * @param plantRegulatoryCode the CAU of the plant this file is being uploaded for; {@code null}
     *                            if the plant has no regulatory code configured yet (rule 2)
     * @param knownCups           CUPS of every supply belonging to the plant's community (rule 8)
     */
    DistributorFileParseResult parse(String filename, byte[] content, String plantRegulatoryCode,
                                      Set<String> knownCups);
}

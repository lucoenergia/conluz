package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The outcome of importing a file of members.",
        requiredProperties = {"registered", "failed", "rows"})
public class ImportMembersResponse {

    @Schema(description = "How many rows were registered.", example = "118")
    private final int registered;
    @Schema(description = "How many rows were not registered. Each has a code and a message in `rows`.",
            example = "2")
    private final int failed;
    @Schema(description = "One result per data row of the file, in the order of the "
            + "file. Rows whose cells are all empty are skipped and have no result.")
    private final List<ImportMembersRowResponse> rows;

    public ImportMembersResponse(int registered, int failed, List<ImportMembersRowResponse> rows) {
        this.registered = registered;
        this.failed = failed;
        this.rows = List.copyOf(rows);
    }

    public int getRegistered() {
        return registered;
    }

    public int getFailed() {
        return failed;
    }

    public List<ImportMembersRowResponse> getRows() {
        return rows;
    }
}

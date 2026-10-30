package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The outcome of importing a file of supplies.",
        requiredProperties = {"created", "failed", "rows"})
public class ImportSuppliesResponse {

    @Schema(description = "How many rows were created.", example = "118")
    private final int created;
    @Schema(description = "How many rows were not created. Each has a code and a message in `rows`.",
            example = "2")
    private final int failed;
    @Schema(description = "One result per data row of the file, in the order of the "
            + "file. Rows whose cells are all empty are skipped and have no result.")
    private final List<ImportSuppliesRowResponse> rows;

    public ImportSuppliesResponse(int created, int failed, List<ImportSuppliesRowResponse> rows) {
        this.created = created;
        this.failed = failed;
        this.rows = List.copyOf(rows);
    }

    public int getCreated() {
        return created;
    }

    public int getFailed() {
        return failed;
    }

    public List<ImportSuppliesRowResponse> getRows() {
        return rows;
    }
}

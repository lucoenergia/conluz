package org.lucoenergia.conluz.domain.admin.supply.create;

import java.util.List;

/**
 * A file of supplies is refused as a whole, before any of its rows is created.
 *
 * <p>{@code headers} are the offending header names, as the file holds them, for the problems about headers; empty
 * otherwise. They are what the caller typed, but a file without a header line holds a supply's data in its first
 * line, so they are never logged.</p>
 */
public class SupplyImportFileRefusedException extends RuntimeException {

    private final SupplyImportFileProblem problem;
    private final List<String> headers;

    public SupplyImportFileRefusedException(SupplyImportFileProblem problem, List<String> headers) {
        // The message names the problem only, so that a log of the exception holds no header
        super(problem.name());
        this.problem = problem;
        this.headers = List.copyOf(headers);
    }

    public SupplyImportFileProblem getProblem() {
        return problem;
    }

    public List<String> getHeaders() {
        return headers;
    }
}

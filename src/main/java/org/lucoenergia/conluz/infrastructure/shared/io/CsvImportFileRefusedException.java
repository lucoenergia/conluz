package org.lucoenergia.conluz.infrastructure.shared.io;

import java.util.List;

/**
 * An imported CSV file is refused as a whole, before any of its rows is imported.
 *
 * <p>{@code headers} are the offending header names, as the file holds them, for the problems about headers; empty
 * otherwise. They are what the caller typed, but a file without a header line holds a row's data in its first line,
 * so they are never logged.</p>
 */
public class CsvImportFileRefusedException extends RuntimeException {

    private final CsvImportFileProblem problem;
    private final List<String> headers;

    public CsvImportFileRefusedException(CsvImportFileProblem problem) {
        this(problem, List.of());
    }

    public CsvImportFileRefusedException(CsvImportFileProblem problem, List<String> headers) {
        // The message names the problem only, so that a log of the exception holds no header
        super(problem.name());
        this.problem = problem;
        this.headers = List.copyOf(headers);
    }

    public CsvImportFileProblem getProblem() {
        return problem;
    }

    public List<String> getHeaders() {
        return headers;
    }
}

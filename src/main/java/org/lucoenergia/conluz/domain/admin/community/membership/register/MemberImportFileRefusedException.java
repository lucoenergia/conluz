package org.lucoenergia.conluz.domain.admin.community.membership.register;

import java.util.List;

/**
 * A file of members is refused as a whole, before any of its rows is registered.
 *
 * <p>{@code headers} are the offending header names, as the file holds them, for the problems about headers; empty
 * otherwise. They are what the caller typed, but a file without a header line holds a member's data in its first
 * line, so they are never logged.</p>
 */
public class MemberImportFileRefusedException extends RuntimeException {

    private final MemberImportFileProblem problem;
    private final List<String> headers;

    public MemberImportFileRefusedException(MemberImportFileProblem problem) {
        this(problem, List.of());
    }

    public MemberImportFileRefusedException(MemberImportFileProblem problem, List<String> headers) {
        // The message names the problem only, so that a log of the exception holds no header
        super(problem.name());
        this.problem = problem;
        this.headers = List.copyOf(headers);
    }

    public MemberImportFileProblem getProblem() {
        return problem;
    }

    public List<String> getHeaders() {
        return headers;
    }
}

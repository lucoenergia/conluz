package org.lucoenergia.conluz.infrastructure.shared.web;

public interface Assembler<R, D> {

    D assemble(R request);
}

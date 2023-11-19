package org.lucoenergia.conluz.domain.shared;

import java.util.List;

public abstract class BaseMapper<P, R> {

    public abstract R map(P point);

    public List<R> mapList(List<P> points) {
        return points.stream()
                .map(this::map)
                .toList();
    }
}

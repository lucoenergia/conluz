package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import java.util.List;

public abstract class BasePointInfluxMapper<P, R> {

    public abstract R map(P point);

    public List<R> mapList(List<P> points) {
        return points.stream()
                .map(this::map)
                .toList();
    }
}

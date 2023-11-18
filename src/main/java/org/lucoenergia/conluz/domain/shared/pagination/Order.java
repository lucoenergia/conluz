package org.lucoenergia.conluz.domain.shared.pagination;


public class Order {

    private final Direction direction;
    private final String property;

    public Order(Direction direction, String property) {
        this.direction = direction;
        this.property = property;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getProperty() {
        return property;
    }
}

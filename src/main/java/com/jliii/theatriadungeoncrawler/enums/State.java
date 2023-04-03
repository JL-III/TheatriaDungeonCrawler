package com.jliii.theatriadungeoncrawler.enums;

public enum State {

    OFF(0),
    LOBBY(1),
    STARTING(2),
    ACTIVE(3),
    WON(4),
    RESTARTING(5);

    public final int order;

    State(int order) {
        this.order = order;
    }

}

package com.jliii.theatriadungeoncrawler.util;

import java.util.Objects;

public class Coord {
    int x;
    int z;

    public Coord(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Coord coord = (Coord) obj;
        return x == coord.x && z == coord.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}


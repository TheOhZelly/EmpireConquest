package com.empireconquest.objects;

public enum EmpTeam {
    SPANISH,
    AZTEC;

    public static EmpTeam fromString(String s) {
        if (s == null) return null;
        return switch (s.toUpperCase()) {
            case "SPANISH" -> SPANISH;
            case "AZTEC"   -> AZTEC;
            default        -> null;
        };
    }

    /** Friendly display color prefix. */
    public String colorPrefix() {
        return this == SPANISH ? "§c" : "§9";
    }

    /** Single-letter abbreviation used in scoreboard. */
    public String abbrev() {
        return this == SPANISH ? "S" : "A";
    }
}

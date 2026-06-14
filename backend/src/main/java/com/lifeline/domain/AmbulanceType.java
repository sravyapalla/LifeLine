package com.lifeline.domain;

public enum AmbulanceType {
    BLS(1, "Basic Life Support"),
    ALS(2, "Advanced Life Support"),
    ICU(3, "Mobile ICU");

    private final int level;
    private final String label;

    AmbulanceType(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    public boolean canHandle(AmbulanceType requiredType) {
        return level >= requiredType.level;
    }
}


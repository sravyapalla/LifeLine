package com.lifeline.domain;

public enum EmergencyCondition {
    CARDIAC("Cardiac"),
    TRAUMA("Trauma"),
    PEDIATRIC("Pediatric"),
    STROKE("Stroke"),
    GENERAL("General");

    private final String label;

    EmergencyCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}


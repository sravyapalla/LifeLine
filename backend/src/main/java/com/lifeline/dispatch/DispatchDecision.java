package com.lifeline.dispatch;

import com.lifeline.domain.Ambulance;
import com.lifeline.domain.Hospital;

import java.util.List;

public record DispatchDecision(
        Ambulance ambulance,
        Hospital hospital,
        CandidateScore winningScore,
        List<CandidateScore> alternatives
) {
}


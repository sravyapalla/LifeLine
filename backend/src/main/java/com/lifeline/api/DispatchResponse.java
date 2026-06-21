package com.lifeline.api;

import com.lifeline.dispatch.CandidateScore;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Trip;

import java.util.List;

public record DispatchResponse(
        IncidentView incident,
        Ambulance ambulance,
        Hospital hospital,
        Trip trip,
        CandidateScore winningScore,
        List<CandidateScore> alternatives
) {
}

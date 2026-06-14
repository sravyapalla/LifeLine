package com.lifeline.dispatch;

public record CandidateScore(
        String ambulanceId,
        String hospitalId,
        double pickupEtaMinutes,
        double hospitalEtaMinutes,
        double hospitalLoad,
        double qualityPenalty,
        double typePenalty,
        double totalCost,
        String explanation
) {
}


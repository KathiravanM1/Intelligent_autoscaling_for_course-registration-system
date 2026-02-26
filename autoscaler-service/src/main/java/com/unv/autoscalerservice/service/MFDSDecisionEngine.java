package com.unv.autoscalerservice.service;

import com.unv.autoscalerservice.model.MFDSFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MFDSDecisionEngine {

    @Value("${sla.tmin}")
    private double tmin;

    @Value("${sla.tmax}")
    private double tmax;

    public ScalingAction decide(MFDSFeatures features) {

        if (features.getLatency() > tmax &&
                features.getArrivalRateDelta() > 0) {
            return ScalingAction.SCALE_OUT;
        }

        if (features.getLatency() < tmin &&
                features.getArrivalRateDelta() < 0) {
            return ScalingAction.SCALE_IN;
        }

        return ScalingAction.NO_ACTION;
    }
}

package logisticslab.constraints;

import com.graphhopper.jsprit.core.problem.solution.SolutionCostCalculator;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;

public class CustomObjectiveFunction implements SolutionCostCalculator {

    private static final double SCALING_PARAM = 0.2d;

    @Override
    public double getCosts(VehicleRoutingProblemSolution solution) {
        var maxTransportTime = 0d;
        var sumTransportTimes = 0d;

        for (var route : solution.getRoutes()) {
            var transportTime = route.getEnd().getArrTime() - route.getStart().getEndTime();
            sumTransportTimes += transportTime;
            if (transportTime > maxTransportTime) {
                maxTransportTime = transportTime;
            }
        }

        return maxTransportTime + SCALING_PARAM * sumTransportTimes;
    }

}
package logisticslab.constraints;

import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.SoftActivityConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.activity.End;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;

public class PenalizeShiftOfMaxTransportTime implements SoftActivityConstraint {

    private final VehicleRoutingTransportCosts routingCosts;
    private final VehicleRoutingActivityCosts activityCosts;
    private final StateManager stateManager;

    public PenalizeShiftOfMaxTransportTime(VehicleRoutingProblem problem, StateManager stateManager) {
        this.routingCosts = problem.getTransportCosts();
        this.activityCosts = problem.getActivityCosts();

        this.stateManager = stateManager;
    }

    @Override
    public double getCosts(JobInsertionContext context, TourActivity previousShipment, TourActivity currentShipment, TourActivity nextShipment, double departureTimeAtPreviousMachine) {

        var maxTime = stateManager.getProblemState(stateManager.createStateId("max-transport-time"), Double.class);
        double currentMaxTransportTime;
        if (maxTime == null || maxTime.isNaN()) {
            currentMaxTransportTime = 0.0;
        } else {
            currentMaxTransportTime = maxTime;
        }

        var transportCostForShipment = routingCosts.getTransportCost(previousShipment.getLocation(), currentShipment.getLocation(), departureTimeAtPreviousMachine, context.getNewDriver(), context.getNewVehicle());
        var transportTimeForShipment = routingCosts.getTransportTime(previousShipment.getLocation(), currentShipment.getLocation(), departureTimeAtPreviousMachine, context.getNewDriver(), context.getNewVehicle());

        var arrivalTimeAtCurrentMachine = departureTimeAtPreviousMachine + transportTimeForShipment;
        var endTimeAtCurrentMachine = Math.max(arrivalTimeAtCurrentMachine, currentShipment.getTheoreticalEarliestOperationStartTime()) + activityCosts.getActivityDuration(currentShipment, arrivalTimeAtCurrentMachine, context.getNewDriver(), context.getNewVehicle());

        // open routes
        double penaltyPerTimeUnitAboveMaxTime = 3d;
        if (nextShipment instanceof End && !context.getNewVehicle().isReturnToDepot()) {
            var routeTransportTime = context.getRoute().getEnd().getArrTime() - context.getRoute().getStart().getEndTime() + transportTimeForShipment;
            return transportCostForShipment + penaltyPerTimeUnitAboveMaxTime * Math.max(0, routeTransportTime - currentMaxTransportTime);
        }

        var transportCostForNextShipment = routingCosts.getTransportCost(currentShipment.getLocation(), nextShipment.getLocation(), endTimeAtCurrentMachine, context.getNewDriver(), context.getNewVehicle());
        var transportTimeForNextShipment = routingCosts.getTransportTime(currentShipment.getLocation(), nextShipment.getLocation(), endTimeAtCurrentMachine, context.getNewDriver(), context.getNewVehicle());

        var totalCosts = transportCostForShipment + transportCostForNextShipment;

        double oldCosts;
        if (context.getRoute().isEmpty()) {
            oldCosts = routingCosts.getTransportCost(previousShipment.getLocation(), nextShipment.getLocation(), departureTimeAtPreviousMachine, context.getNewDriver(), context.getNewVehicle());
        } else {
            oldCosts = routingCosts.getTransportCost(previousShipment.getLocation(), nextShipment.getLocation(), previousShipment.getEndTime(), context.getRoute().getDriver(), context.getRoute().getVehicle());
        }

        var arrivalTimeAtNextMachine = endTimeAtCurrentMachine + transportTimeForNextShipment;
        double oldTime;
        if (context.getRoute().isEmpty()) {
            oldTime = (nextShipment.getArrTime() - departureTimeAtPreviousMachine);
        } else {
            oldTime = (nextShipment.getArrTime() - context.getRoute().getDepartureTime());
        }

        var additionalTime = (arrivalTimeAtNextMachine - context.getNewDepTime()) - oldTime;
        var transportTime = context.getRoute().getEnd().getArrTime() - context.getRoute().getStart().getEndTime() + additionalTime;

        return totalCosts - oldCosts + penaltyPerTimeUnitAboveMaxTime * Math.max(0, transportTime - currentMaxTransportTime);

    }
}
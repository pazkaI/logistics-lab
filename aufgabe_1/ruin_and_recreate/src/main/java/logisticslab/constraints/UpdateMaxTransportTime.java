package logisticslab.constraints;

import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.state.StateUpdater;
import com.graphhopper.jsprit.core.problem.cost.ForwardTransportTime;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.ActivityVisitor;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.util.ActivityTimeTracker;

public class UpdateMaxTransportTime implements ActivityVisitor, StateUpdater {

    private final StateManager stateManager;
    private final ActivityTimeTracker timeTracker;

    public UpdateMaxTransportTime(StateManager stateManager, ForwardTransportTime transportTime, VehicleRoutingActivityCosts activityCosts) {
        super();

        this.stateManager = stateManager;
        this.timeTracker = new ActivityTimeTracker(transportTime, activityCosts);
    }

    @Override
    public void begin(VehicleRoute route) {
        timeTracker.begin(route);
    }

    @Override
    public void visit(TourActivity activity) {
        timeTracker.visit(activity);
    }

    @Override
    public void finish() {
        timeTracker.finish();

        var newRouteEndTime = timeTracker.getActArrTime();

        var maxTime = stateManager.getProblemState(stateManager.createStateId("max-transport-time"),  Double.class);
        double currentMaxTransportTime;

        if (maxTime == null || maxTime.isNaN()) {
            currentMaxTransportTime = 0.0;
        } else {
            currentMaxTransportTime = maxTime;
        }

        if (newRouteEndTime > currentMaxTransportTime) {
            stateManager.putProblemState(stateManager.createStateId("max-transport-time"), Double.class, newRouteEndTime);
        }
    }

}
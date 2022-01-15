package logisticslab;

import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import logisticslab.constraints.CustomObjectiveFunction;
import logisticslab.constraints.PenalizeShiftOfMaxTransportTime;
import logisticslab.constraints.UpdateMaxTransportTime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ScheduleGenerator {

    /**
     * Creates a schedule utilizing the given number of vehicles.
     *
     * @param numVehicles the number of vehicles
     */
    public ScheduleGenerator(int numVehicles) {

        // parse machines
        var machines = createMachines();
        
        // build vehicles
        var vehicles = createVehicles(numVehicles, machines);
        
        // parse shipments
        var shipments = createShipments(machines);

        // run routing algorithm
        var routes = runRoutingAlgorithm(vehicles, shipments);

        // convert to schedule entries
        var scheduleEntries = convertToScheduleEntries(routes, machines);
        var combinedEntries = combineEntries(scheduleEntries);

        // export schedule
        exportSchedule(combinedEntries);
    }

    /**
     * Parses the machines from machines.txt.
     *
     * @return the list of machines
     */
    private List<Machine> createMachines() {
        var machines = new ArrayList<Machine>();

        var path = Paths.get("machines.txt");
        try (var stream = Files.lines(path)) {
            stream.forEachOrdered(line -> {
                if (!line.startsWith("id")) {
                    var parts = line.split(";");

                    var id = parts[0];
                    var x = Integer.parseInt(parts[1]);
                    var y = Integer.parseInt(parts[2]);

                    machines.add(new Machine(id, Location.newInstance(x, y)));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return machines;
    }

    /**
     * Creates a number of vehicles.
     *
     * <p>
     * The initial vehicle position is set to the position of the machine with the same id.
     * I.e vehicle 2 is located at machine 2 and so on.
     *
     * @param numVehicles the number of vehicles to create
     * @param machines the list of machines (to set initial vehicle locations)
     * @return the list of vehicles
     */
    private List<Vehicle> createVehicles(int numVehicles, List<Machine> machines) {
        var vehicles = new ArrayList<Vehicle>();

        for (int i = 0; i < numVehicles; i++) {
            var id = String.valueOf(i+1);
            var location = Machine.findById(id, machines).location();

            var vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance(id + "_type").addCapacityDimension(0, 1).setCostPerDistance(1d);
            var vehicleType = vehicleTypeBuilder.build();

            var vehicle = VehicleImpl.Builder.newInstance(String.valueOf(i+1))
                    .setStartLocation(location)
                    .setType(vehicleType)
                    .setReturnToDepot(false)
                    .build();

            vehicles.add(vehicle);
        }

        return vehicles;
    }

    /**
     * Parses the shipments from transport_demand.txt
     *
     * <p>
     * Shipment locations correspond to machine locations.
     *
     * @param machines the list of machines
     * @return the list of shipments
     */
    private List<Shipment> createShipments(List<Machine> machines) {
        var shipments = new ArrayList<Shipment>();

        var path = Paths.get("transport_demand.txt");
        try (var stream = Files.lines(path)) {
            stream.forEachOrdered(line -> {
                if (!line.startsWith("start")) {
                    var parts = line.split(";");
                    var from = Machine.findById(parts[0], machines);
                    var to = Machine.findById(parts[1], machines);
                    var count = Integer.parseInt(parts[2]);

                    for (int i=0; i < count; i++) {
                        var shipment = Shipment.Builder.newInstance(UUID.randomUUID().toString())
                                .addSizeDimension(0, 1)
                                .setPickupLocation(from.location())
                                .setDeliveryLocation(to.location())
                                .build();

                        shipments.add(shipment);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return shipments;
    }

    /**
     * Runs the routing algorithm and creates a collection of vehicle routes.
     *
     * @param vehicles the list of vehicles to use
     * @param shipments the list of shipments that need to be completed
     * @return the collection of calculated vehicle routes
     */
    private Collection<VehicleRoute> runRoutingAlgorithm(List<Vehicle> vehicles, List<Shipment> shipments) {

        var problemBuilder = VehicleRoutingProblem.Builder.newInstance();

        // add vehicles
        for (var vehicle : vehicles) {
            problemBuilder.addVehicle(vehicle);
        }

        problemBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        // add shipments
        for (var shipment : shipments) {
            problemBuilder.addJob(shipment);
        }

        // build problem
        var problem = problemBuilder.build();

        var stateManager = new StateManager(problem);

        // keep track of transport time
        var maxTransportTimeStateId = stateManager.createStateId("max-transport-time");
        stateManager.putProblemState(maxTransportTimeStateId, Double.class, 0d);
        stateManager.addStateUpdater(new UpdateMaxTransportTime(stateManager, problem.getTransportCosts(), problem.getActivityCosts()));

        // penalize shift of max transport time
        var constraintManager = new ConstraintManager(problem, stateManager);
        constraintManager.addConstraint(new PenalizeShiftOfMaxTransportTime(problem, stateManager));

        // build algorithm
        var algorithm = Jsprit.Builder.newInstance(problem)
                .setStateAndConstraintManager(stateManager, constraintManager)
                // add custom objective function
                .setObjectiveFunction(new CustomObjectiveFunction()).buildAlgorithm();

        algorithm.setMaxIterations(2000);

        // search solutions
        var solutions = algorithm.searchSolutions();

        // take the best one
        var bestSolution = Solutions.bestOf(solutions);
        return bestSolution.getRoutes();
    }

    /**
     * Converts vehicle routes to schedule entries.
     *
     * @param routes the list of routes
     * @param machines the list of machines (to correlate shipment position to machines)
     * @return the list of schedule entries
     */
    private List<ScheduleEntry> convertToScheduleEntries(Collection<VehicleRoute> routes, List<Machine> machines) {
        var entries = new ArrayList<ScheduleEntry>();

        // sort routes by vehicle id (needed for validation)
        var routeList = routes.stream().sorted(Comparator.comparing(r -> r.getVehicle().getId())).toList();

        for (var route : routeList) {
            var vehicleId = route.getVehicle().getId();

            // if first shipment does not start at route start, add schedule entry (needed for validation)
            var routeStart = route.getStart().getLocation();
            var firstShipmentLocation = route.getActivities().get(0).getLocation();
            if (!routeStart.equals(firstShipmentLocation)) {
                var machine = Machine.findByLocation(routeStart, machines);
                entries.add(new ScheduleEntry(vehicleId, machine, false, false));
            }

            // convert shipments to schedule entries
            for (var activity : route.getActivities()) {
                var location = activity.getLocation();
                var machine = Machine.findByLocation(location, machines);

                var load = activity.getName().equals("pickupShipment");
                var unload = activity.getName().equals("deliverShipment");

                entries.add(new ScheduleEntry(vehicleId, machine, load, unload));
            }
        }

        return entries;
    }

    /**
     * Combines load and unload operations if possible.
     *
     * <p>
     * This is needed for validation.
     *
     * @param entries the list of schedule entries
     * @return the updated list of schedule entries
     */
    private List<ScheduleEntry> combineEntries(List<ScheduleEntry> entries) {
        var updated = new ArrayList<ScheduleEntry>();

        int index = 0;
        while (index < entries.size()) {
            var entry = entries.get(index);

            if (index < entries.size() - 1) {
                var nextEntry = entries.get(index + 1);

                // combine load and unload at same machine if possible
                if (entry.vehicleId().equals(nextEntry.vehicleId()) && entry.location().equals(nextEntry.location()) && entry.unload() && nextEntry.load()) {
                    updated.add(new ScheduleEntry(entry.vehicleId(), entry.location(), true, true));

                    // move index by 2 (because two entries have been combined)
                    index = index + 2;
                    continue;
                }
            }

            // if we cant combine an entry, just pass it along
            updated.add(entry);
            index++;
        }

        return updated;
    }

    /**
     * Exports the given schedule entries to schedule.txt
     *
     * @param combinedEntries the schedule entries
     */
    private void exportSchedule(List<ScheduleEntry> combinedEntries) {
        var path = Paths.get("schedule.txt");
        var lines = combinedEntries.stream().map(ScheduleEntry::toString).toList();
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

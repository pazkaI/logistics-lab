package logisticslab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public class SimpleShortestPaths {

    static final int NUM_VEHICLES = 1;

    static final List<Machine> machines = new ArrayList<>();

    static final List<Demand> demands = new ArrayList<>();

    static final List<Vehicle> vehicles = new ArrayList<>();

    static final List<ScheduleEntry> scheduleEntries = new ArrayList<>();

    public static void main(String[] args) {
        parseMachines();
        parseDemand();
        initVehicles();

        greedy();

        buildTransports();
        export();
    }

    private static void export() {
        var path = Paths.get("schedule.txt");
        var lines = scheduleEntries.stream().map(ScheduleEntry::toString).toList();
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }

        vehicles.stream().max(Comparator.comparing(Vehicle::getTravelDistance)).ifPresent(vehicle -> System.out.println(vehicle.getTravelDistance()));
    }

    private static void buildTransports() {
        for (var vehicle : vehicles) {
            var routes = vehicle.getRoutes();

            for (int index = 0; index < routes.size(); index++) {
                var route = routes.get(index);

                if (index < routes.size() - 1) {
                    var nextRoute = routes.get(index + 1);

                    if (route.destination().equals(nextRoute.destination()) && route.unload() && nextRoute.load()) {
                        var entry = new ScheduleEntry(vehicle.getIdForSchedule(), route.destination(), true, true);
                        scheduleEntries.add(entry);

                        index++;
                        continue;
                    }
                }

                var entry = new ScheduleEntry(vehicle.getIdForSchedule(), route.destination(), route.load(), route.unload());
                scheduleEntries.add(entry);
            }

        }
    }

    private static void greedy() {
        while (!demands.isEmpty()) {
            // find vehicle with least distance traveled
            var vehicle = vehicles.stream().min(Comparator.comparing(Vehicle::getTravelDistance)).orElseThrow();

            // find demand which can be fulfilled with least cost
            var demand = argmin(demands.stream(), d -> distanceNeededToFulfillDemand(vehicle, d));
            if (demand == null) {
                throw new IllegalStateException("meh");
            }

            // add route for demand to vehicle
            vehicle.addRoute(new Route(demand.from(), true, false));
            vehicle.addTravelDistance(vehicle.position().distance(demand.from().position()));
            vehicle.setPosition(demand.from().position());

            vehicle.addRoute(new Route(demand.to(), false, true));
            vehicle.addTravelDistance(demand.from().distance(demand.to()));
            vehicle.setPosition(demand.to().position());

            demand.decreaseCount();
            if (demand.count() == 0) {
                demands.remove(demand);
            }
        }
    }

    /**
     * Parses the machines from a file.
     */
    private static void parseMachines() {
        var path = Paths.get("machines.txt");
        try (var stream = Files.lines(path)) {
            stream.forEachOrdered(line -> {
                if (!line.startsWith("id")) {
                    var parts = line.split(";");
                    var id = parts[0];
                    var x = Integer.parseInt(parts[1]);
                    var y = Integer.parseInt(parts[2]);
                    machines.add(new Machine(id, new Position(x, y)));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the transport demand from a file.
     */
    private static void parseDemand() {
        var path = Paths.get("transport_demand.txt");
        try (var stream = Files.lines(path)) {
            stream.forEachOrdered(line -> {
                if (!line.startsWith("start")) {
                    var parts = line.split(";");
                    var from = getMachineById(parts[0]);
                    var to = getMachineById(parts[1]);
                    var count = Integer.parseInt(parts[2]);
                    demands.add(new Demand(from, to, count));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // remove demand with 0 count
        demands.removeIf(demand -> demand.count == 0);
    }

    private static void initVehicles() {
        for (int i = 0; i < NUM_VEHICLES; i++) {
            var initialPosition = machines.get(i).position();
            var vehicle = new Vehicle(i, initialPosition);

            vehicles.add(vehicle);
        }
    }

    /**
     * Returns a machine object by its id.
     *
     * @param id the machine id
     * @return the machine object
     */
    private static Machine getMachineById(String id) {
        return machines.stream().filter(m -> Objects.equals(m.id(), id)).findFirst().orElseThrow();
    }

    /**
     * Calculates the distance needed to fulfill the given demand with the given vehicle.
     *
     * <p>
     * It's the sum of the distance from the current vehicle position to the demand start and the distance of the demand start to demand end
     *
     * @param vehicle the vehicle
     * @param demand the demand
     * @return the needed distance
     */
    private static double distanceNeededToFulfillDemand(Vehicle vehicle, Demand demand) {
        return vehicle.position().distance(demand.from().position()) + demand.from().distance(demand.to());
    }

    private static <T> T argmin(Stream<T> stream, ToDoubleFunction<T> scorer) {
        Double min = null;
        T argmin = null;
        for (T p: (Iterable<T>) stream::iterator) {
            double score = scorer.applyAsDouble(p);
            if (min==null || min > score) {
                min = score;
                argmin = p;
            }
        }
        return argmin;
    }

    /**
     * Record that represents a machine with an id and xy coordinates.
     */
    record Machine(String id, Position position) {

        public double distance(Machine other) {
            return this.position.distance(other.position);
        }

    }

    /**
     * Class representing a demand as given in transport_demand.txt.
     */
    static class Demand {
        private final Machine from;
        private final Machine to;
        private int count;

        public Demand(Machine from, Machine to, int count) {
            this.from = from;
            this.to = to;
            this.count = count;
        }

        public Machine from() {
            return from;
        }

        public Machine to() {
            return to;
        }

        public int count() {
            return count;
        }

        public void decreaseCount() {
            count--;
        }
    }

    /**
     * Record representing a transport as needed in schedule.txt.
     */
    record ScheduleEntry(String vehicleId, Machine location, boolean load, boolean unload) {

        @Override
        public String toString() {
            var loadString = load ? "1": "0";
            var unloadString = unload ? "1" : "0";

            return vehicleId + ";" + location.id() + ";" + unloadString + ";" + loadString;
        }
    }

    static class Vehicle {
        private final int id;
        private Position position;
        private double travelDistance = 0;

        private final List<Route> routes = new ArrayList<>();

        public Vehicle(int id, Position position) {
            this.id = id;
            this.position = position;
        }

        public int getId() {
            return this.id;
        }

        public String getIdForSchedule() {
            return String.valueOf(this.id + 1);
        }

        public double getTravelDistance() {
            return this.travelDistance;
        }

        public void addRoute(Route route) {
            this.routes.add(route);
        }

        public List<Route> getRoutes() {
            return this.routes;
        }

        public void addTravelDistance(double distance) {
            this.travelDistance += distance;
        }

        public void setPosition(Position position) {
            this.position = position;
        }

        public Position position() {
            return position;
        }
    }

    record Route(Machine destination, boolean load, boolean unload) {

    }

    static class Position {
        private int x;
        private int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        public double distance(Position other) {
            double distX = Math.abs(x - other.x);
            double distY = Math.abs(y - other.y);
            return Math.sqrt(distX*distX + distY*distY);
        }
    }
}

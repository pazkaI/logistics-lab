package logisticslab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A simple algorithm for calculating a transport schedule.
 *
 * <p>
 * Decides the next location by looking at the closest machine where any current "outgoing" demand can be met first. If there is no more "outgoing" demand at the current machine,
 * go to the closest machine that still has some "outgoing" demand.
 */
public class SimpleShortestPaths {

    /**
     * The list of machines.
     */
    static final List<Machine> machines = new ArrayList<>();

    /**
     * A list to keep track of the current demands.
     */
    static final List<Demand> demands = new ArrayList<>();

    /**
     * A list containing the calculated transports.
     */
    static final List<Transport> transports = new ArrayList<>();

    /**
     * The main method.
     */
    public static void main(String[] args) {
        createMachines();
        importTransports();

        nextTransport(getById("1"), null, false);

        writeSchedule();
    }

    /**
     * Creates a number of machines at the given coordinates.
     */
    private static void createMachines() {
        machines.add(new Machine("1", 5, 5));
        machines.add(new Machine("2", 50, 5));
        machines.add(new Machine("3", 70, 25));
        machines.add(new Machine("4", 60, 40));
        machines.add(new Machine("5", 30, 40));
        machines.add(new Machine("6", 5, 25));
    }

    /**
     * Parses the transport demand from a file.
     */
    private static void importTransports() {
        var path = Paths.get("transport_demand.txt");
        try (var stream = Files.lines(path)) {
            stream.forEachOrdered(line -> {
                if (!line.startsWith("start")) {
                    var parts = line.split(";");
                    var from = getById(parts[0]);
                    var to = getById(parts[1]);
                    var count = Integer.parseInt(parts[2]);
                    demands.add(new Demand(from, to, count));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the next transport (which equals a single line in the schedule)
     *
     * @param current the current machine
     * @param previous the previous machine
     * @param isLoaded whether the transport from the previous to the current machine was loaded
     */
    private static void nextTransport(Machine current, Machine previous, boolean isLoaded) {
        if (isLoaded) {
            // reduce count in demand list
            var demand = getByOriginAndTarget(previous, current);
            var index = demands.indexOf(demand);
            demands.get(index).decreaseCount();
        }

        // check if there is demand from current to somewhere
        var destinationsWithDemand = calculateDestinationsWithDemand(current);
        if (!destinationsWithDemand.isEmpty()) {
            // find closest machine that satisfies demand
            var target = selectClosest(current, destinationsWithDemand);

            // write transport
            transports.add(new Transport("1", current, true, isLoaded));

            // do next transport
            nextTransport(target, current, true);
        } else {
            // find nodes that still have demand (excluding the current node)
            var machinesWithDemand = calculateMachinesWithDemandExcluding(current);
            if (!machinesWithDemand.isEmpty()) {
                // find closest
                var target = selectClosest(current, machinesWithDemand);

                // write transport
                transports.add(new Transport("1", current, false, isLoaded));

                // do next transport
                nextTransport(target, current, false);
            } else {
                // we are done, unload at last location
                transports.add(new Transport("1", current, false, isLoaded));
            }
        }
    }

    /**
     * Returns the machine from {@code destinations} that has the least distance from {@code origin}.
     *
     * @param origin the current machine
     * @param destinations the list of possible destinations
     * @return the closest machine from {@code destinations}
     */
    private static Machine selectClosest(Machine origin, List<Machine> destinations) {
        return destinations.stream().reduce((a, b) -> origin.distance(a) >= origin.distance(b) ? a : b).get();
    }

    /**
     * Returns a list of all machines (excluding the given machine) that have any "outgoing" demand.
     *
     * @param current the current machine
     * @return a list of machines that have demand
     */
    private static List<Machine> calculateMachinesWithDemandExcluding(Machine current) {
        return demands.stream()
                .filter(demand -> demand.from() != current)
                .filter(demand -> demand.count > 0)
                .map(Demand::from)
                .toList();
    }

    /**
     * Returns all machines where an "incoming" demand exists from the given machine.
     *
     * @param current the given machine.
     * @return a list of machines that have a demand from the given machine
     */
    private static List<Machine> calculateDestinationsWithDemand(Machine current) {
        return demands.stream()
                .filter(demand -> demand.from() == current)
                .filter(demand -> demand.count() > 0)
                .map(Demand::to)
                .toList();
    }

    /**
     * Returns a machine object by its id.
     *
     * @param id the machine id
     * @return the machine object
     */
    private static Machine getById(String id) {
        return machines.stream().filter(m -> Objects.equals(m.id(), id)).findFirst().orElseThrow();
    }

    /**
     * Returns the demand object with the given origin and target machines.
     *
     * @param origin the machine at the origin
     * @param target the machine at the target
     * @return the demand object
     */
    private static Demand getByOriginAndTarget(Machine origin, Machine target) {
        return demands.stream().filter(demand -> demand.from() == origin && demand.to() == target).findFirst().orElseThrow();
    }

    /**
     * Writes the calculated schedule to a file.
     */
    private static void writeSchedule() {
        var path = Paths.get("schedule.txt");
        var lines = transports.stream().map(Transport::toScheduleEntry).toList();
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Record that represents a machine with an id and xy coordinates.
     */
    record Machine(String id, int x, int y) {

        public double distance(Machine other) {
            double distX = Math.abs(x - other.x);
            double distY = Math.abs(y - other.y);
            return Math.sqrt(distX*distX + distY*distY);
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
    record Transport(String vehicleId, Machine location, boolean load, boolean unload) {

        public String toScheduleEntry() {
            var loadString = load ? "1": "0";
            var unloadString = unload ? "1" : "0";

            return vehicleId + ";" + location.id() + ";" + unloadString + ";" + loadString;
        }
    }

}

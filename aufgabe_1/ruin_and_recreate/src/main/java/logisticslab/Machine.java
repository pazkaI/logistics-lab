package logisticslab;

import com.graphhopper.jsprit.core.problem.Location;

import java.util.List;
import java.util.Objects;

record Machine(String id, Location location) {

    /**
     * Finds a machine by its id.
     *
     * @param id the machine id
     * @param machines the list of machines to search
     * @return the machine object
     */
    static Machine findById(String id, List<Machine> machines) {
        return machines.stream().filter(m -> Objects.equals(m.id(), id)).findFirst().orElseThrow();
    }

    /**
     * Finds a machine by its location.
     *
     * @param location the location
     * @param machines the list of machines to search
     * @return the machine object
     */
    static Machine findByLocation(Location location, List<Machine> machines) {
        return machines.stream().filter(m -> Objects.equals(m.location(), location)).findFirst().orElseThrow();
    }

}
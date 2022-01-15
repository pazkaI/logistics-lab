package logisticslab;

/**
 * Represents an entry in the schedule.txt.
 */
record ScheduleEntry(String vehicleId, Machine location, boolean load, boolean unload) {

    @Override
    public String toString() {
        var loadString = load ? "1": "0";
        var unloadString = unload ? "1" : "0";

        return vehicleId + ";" + location.id() + ";" + unloadString + ";" + loadString;
    }
}
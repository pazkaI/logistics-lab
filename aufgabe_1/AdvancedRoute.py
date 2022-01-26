import argparse
import csv
from sys import stderr
import numpy as np
from dataclasses import dataclass, field

##### Global Stuff
tasks: list["Task"] = []
machines: list["Machine"] = []
vehicles: list["Vehicle"] = []


##### Helpers
def get_machine(id):
    """Get machine by ID"""
    return [m for m in machines if m.id == id][0]


def find_machine(pos):
    """Find machine by position"""
    return [m for m in machines if m.position == pos][0]


def distance(p1, p2):
    """Euclidean distance between vehicle and it's target or other machine"""
    return np.linalg.norm(np.asarray(p1) - np.asarray(p2))


##### Base classes
@dataclass
class Task:
    id: int
    start: int
    destination: int
    dummy: bool = False

    @property
    def spos(self):
        return get_machine(self.start).position

    @property
    def dpos(self):
        return get_machine(self.destination).position


@dataclass
class Machine:
    id: int
    position: tuple[float, float]

    @property
    def tasks(self) -> list[Task]:
        global tasks
        return list(filter(lambda x: x.start == self.id, tasks))


@dataclass
class Vehicle:
    id: int
    position: tuple[float, float]
    tasks: list[Task] = field(default_factory=list)
    done: list[Task] = field(default_factory=list)

    @property
    def distance(self):
        if not self.tasks or len(self.tasks) == 0:
            return 0
        return distance(
            self.position,
            self.tasks[0].dpos,
        )

    def move(self, distance):
        """Move vehicle by distance"""
        if len(self.tasks) > 0:
            if distance >= self.distance:
                self.position = self.tasks[0].dpos
                self.done.append(self.tasks.pop(0))
            else:
                direction = (np.asarray(self.tasks[0].dpos) - np.asarray(self.position)) / self.distance
                self.position = tuple(
                    self.position + (direction * distance)
                )

    def finish(self):
        """Finish all tasks, move to done tasks"""
        self.done += self.tasks
        self.tasks.clear()


#### logic code
def get_best_task(options):
    """Select best task of a machine"""
    global tasks
    # Lower means better
    def good_task(task: Task):
        return (
            #len(get_machine(task.destination).tasks) > 0, # Hat Anschluss tasks (<- nicht relevant wegen der nächsten)
            len(get_machine(task.destination).tasks),  # Maschine mit den meisten anderen aufträgen
            -distance(task.spos, task.dpos), # Nah dran
            #len([x for x in machines if x.tasks and x.tasks[-1].destination == task.start]) == 0, # Kein anderer fährt hin
            )

    s_tasks = sorted(options, key=good_task, reverse=True)  # Sorted tasks
    print(s_tasks, file=stderr)
    if len(s_tasks) > 0:
        return s_tasks[0]
    return None


def assign_task(vehicle):
    """Assign new task to vehicle"""
    global tasks

    # Current machine
    machine = find_machine(vehicle.position)
    # Best task at our position
    best = get_best_task(machine.tasks)
    if best:
        # If found remove from available tasks and assign to us
        tasks.remove(best)
        vehicle.tasks.append(best)
    else:
        # Not found so drive to next best machine and assign tasks
        next_machine = sorted(
            machines,
            key=lambda m: (
                #len(m.tasks) > 0, # Maschine hat aufgaben!
                len(m.tasks), # selben wie oben, aber wir bevorzugen maschinen mit vielen aufgaben
                len([v for v in vehicles if v.tasks and v.tasks[-1].destination == m.id]) == 0, # Kein anderer fährt zu dieser maschine
                #-distance(m.position, vehicle.position),
            ),
            reverse=True,
        )[0]
        vehicle.tasks.append(Task(-1, machine.id, next_machine.id, dummy=True))
        best = get_best_task(next_machine.tasks)
        tasks.remove(best)
        vehicle.tasks.append(best)


def loop():
    global vehicles
    vehicles.sort(key=lambda v: v.distance)
    distance = vehicles[0].distance

    for vehicle in vehicles:
        vehicle.move(distance + 0.00000001)
        if len(vehicle.tasks) == 0:
            assign_task(vehicle)


##### Init code
def init_machines():
    global machines
    for i, m in enumerate([(5, 5), (50, 5), (70, 25), (60, 40), (30, 40), (5, 25)]):
        machines.append(Machine(i + 1, m))


def init_tasks(transport_demand):
    global tasks
    idx = 0
    for td in transport_demand:
        for j in range(int(td["number"])):
            tasks.append(Task(idx, int(td["start"]), int(td["dest"])))
            idx += 1


def init_vehicles(num):
    for idx in range(1, num + 1):
        vehicles.append(Vehicle(idx, get_machine(idx).position))


##### Main stuff
if __name__ == "__main__":
    # command line options
    parser = argparse.ArgumentParser(description="Process some integers.")
    parser.add_argument("--demand", type=str, default="transport_demand.txt")
    parser.add_argument("--output", type=str, default="schedule.txt")
    parser.add_argument("--vehicles", type=int, default=1)
    args = parser.parse_args()
    # Init machines
    init_machines()
    # Init vehicles
    if args.vehicles < 1 or args.vehicles > len(machines):
        raise RuntimeError("Invalid number of vehicles")
    init_vehicles(args.vehicles)
    # Init tasks
    with open(args.demand, "r") as csv_file:
        init_tasks(list(csv.DictReader(csv_file, delimiter=";", quoting=csv.QUOTE_NONE)))

    # RUN
    while len(tasks) > 0:
        loop()
    vehicles.sort(key=lambda x: x.id)
    for v in vehicles:
        v.finish()
        for idx, done in enumerate(v.done):
            print(f"{v.id};{done.start};{1 if idx > 0 and not v.done[idx-1].dummy else 0};{0 if done.dummy else 1};")
        print(f"{v.id};{v.done[-1].destination};1;0;")

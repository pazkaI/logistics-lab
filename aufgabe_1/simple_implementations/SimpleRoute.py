import numpy as np
import argparse
import csv

# coords of out machines
machines = np.array([(5, 5), (50, 5), (70, 25), (60, 40), (30, 40), (5, 25)])

# parsed from file
transport_demand = []

# Our vehicle objects
vehicles = []

# Paths of our vehicles
paths = []


def mpos(idx):
    """Get machine position by machine idx"""
    if idx is None:
        raise ValueError
    return machines[idx, :].copy()


def midx(pos):
    """Get index of machine by its position"""
    return [
        idx
        for idx in range(machines.shape[0])
        if np.array_equal(
            machines[
                idx,
            ],
            pos,
        )
    ][0]


def distance(vehicle, target=None):
    """Euclidean distance between vehicle and it's target or other machine"""
    if target is None:
        target = vehicle["target"]
    if target is None:
        return 0
    return np.linalg.norm(vehicle["pos"] - mpos(target))


def tasks(start):
    """Get allopen tasks at machine at position start"""
    if not isinstance(start, int):
        start = midx(start)
    return [t for t in transport_demand if t["start"] == start and t["number"] > 0]


def open_demands():
    """Get all global open tasks"""
    return [t for t in transport_demand if t["number"] > 0]


def init(args):
    """Read target demands and init vehicles"""
    global vehicles
    global transport_demand
    with open(args.demand, "r") as csv_file:
        transport_demand = list(
            csv.DictReader(csv_file, delimiter=";", quoting=csv.QUOTE_NONE)
        )
        for td in transport_demand:
            for idx in td:
                td[idx] = int(td[idx]) - (1 if idx != "number" else 0)

    if args.vehicles < 1 or args.vehicles > len(machines):
        raise RuntimeError("Invalid number of vehicles")
    for idx in range(args.vehicles):
        vehicles.append(
            {
                "id": idx,
                "pos": mpos(idx),
                "cargo": False,
                "target": None,
                "dist": 0,
            }
        )


def calculate():
    while (
        len(open_demands()) > 0 or len([v for v in vehicles if v["cargo"] == True]) > 0
    ):
        first = sorted(vehicles, key=distance, reverse=len(open_demands()) > 0).pop()

        # Move vehicles
        d = distance(first)
        for v in vehicles:
            if v["target"] is not None:
                v["pos"] = (mpos(v["target"]) - v["pos"]) / distance(v) * d + v["pos"]
                if np.linalg.norm(v["pos"] - mpos(v["target"])) <= 0:
                    v["pos"] = mpos(v["target"])
                    v["target"] = None

        # Assign new tasks
        for vehicle in vehicles:
            if vehicle["target"] == None:
                # Select open transport task at current position
                # if there is none, got to the nearest
                t = tasks(vehicle["pos"])
                if len(t) == 0:
                    t = sorted(
                        open_demands(), key=lambda t: distance(vehicle, t["dest"])
                    )
                    if len(t) == 0:
                        if vehicle["cargo"] == True:
                            paths.append((vehicle["id"], midx(vehicle["pos"]), 1, 0))
                            vehicle["cargo"] = False
                        print("Are we ready?????")
                        continue
                # select a task with a connected next one if possible
                t = sorted(
                    t,
                    key=lambda tt: len(
                        [ttt for ttt in transport_demand if ttt["dest"] == tt["dest"]]
                    ),
                    reverse=True,
                )
                print(
                    f"vehicle: {vehicle['id']}, current: {vehicle['pos']}, new target: {t[0]['dest']} ({mpos(t[0]['dest'])})"
                )

                if np.array_equal(vehicle["pos"], mpos(t[0]["start"])):
                    vehicle["target"] = t[0]["dest"]
                    t[0]["number"] -= 1
                    if vehicle["cargo"] == True:
                        paths.append((vehicle["id"], t[0]["start"], 1, 1))
                    else:
                        paths.append((vehicle["id"], t[0]["start"], 0, 1))
                    vehicle["cargo"] = True
                else:
                    paths.append((vehicle["id"], midx(vehicle["pos"]), 1, 0))
                    vehicle["cargo"] = False
                    vehicle["target"] = t[0]["start"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Process some integers.")
    parser.add_argument("--demand", type=str, default="transport_demand.txt")
    parser.add_argument("--output", type=str, default="schedule.txt")
    parser.add_argument("--vehicles", type=int, default=1)

    args = parser.parse_args()
    init(args)
    calculate()

    with open(args.output, "w") as csv_file:
        w = csv.writer(csv_file, delimiter=";", quoting=csv.QUOTE_NONNUMERIC)
        for i in range(args.vehicles):
            p = [p for p in paths if p[0] == i]
            for pp in p:
                pp = list(pp)
                pp[0] += 1
                pp[1] += 1
                w.writerow(pp)

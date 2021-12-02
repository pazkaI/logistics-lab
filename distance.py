import pandas
from scipy.spatial import distance_matrix

coords = [[5, 5], [50, 5], [70, 25], [60, 40], [30, 40], [5, 25]]
machineIndices = [1, 2, 3, 4, 5, 6]

machines = pandas.DataFrame(coords, columns=['X', 'Y'], index=machineIndices)
distances = pandas.DataFrame(distance_matrix(machines.values, machines.values), index=machines.index, columns=machines.index)

print(distances)
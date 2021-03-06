#!/usr/bin/env pybricks-micropython
from pybricks.hubs import EV3Brick
from pybricks.ev3devices import Motor
from pybricks.parameters import Port
from pybricks.robotics import DriveBase
from pybricks.tools import StopWatch
from pybricks.tools import wait

ev3 = EV3Brick()
left_motor = Motor(Port.C)
right_motor = Motor(Port.B)
robot = DriveBase(left_motor, right_motor, wheel_diameter=56, axle_track=143)
speed = 440 #seems to be the maximum speed
watch = StopWatch()

while left_motor.angle() < 3870: #3870 Degrees Wheelspin was closest to 2 Meters
    robot.drive(speed, 0)
robot.stop()
time = watch.time()
ev3.screen.print(time)
wait(5000)
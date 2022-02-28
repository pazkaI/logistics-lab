#!/usr/bin/env pybricks-micropython
from pybricks.hubs import EV3Brick
from pybricks.ev3devices import Motor, ColorSensor
from pybricks.parameters import Port
from pybricks.robotics import DriveBase
from pybricks.tools import StopWatch
from pybricks.tools import wait

ev3 = EV3Brick()
left_motor = Motor(Port.C)
right_motor = Motor(Port.B)
color_sensor = ColorSensor(Port.S1)
robot = DriveBase(left_motor, right_motor, wheel_diameter=56, axle_track=143)
speed = 440
watch = StopWatch()

#calibrating threshhold
white = 32
black = 3
threshold = (white + black)/2

#Parameter for proportional Controller
Kp = -1.2

while left_motor.angle() < 3870:
    error = color_sensor.reflection() - threshold

    #proportional Controller calculation
    turn_rate = Kp * error

    robot.drive(speed, turn_rate)
robot.stop()
time = watch.time()
ev3.screen.print(time)
wait(5000)
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
watch = StopWatch()

#calibrating threshhold
white = 32
black = 3
threshold = (white + black)/2

# initial values for PID Controller
lastInput = 0
i = 0
d = 0

# PID-Parameters (fitting Parameters for higher speeds, but still robust execution, could not be found (in realistic amount of time and effort))
speed = 140
Kp = 5.5
Ki = 0.02
Kd = 0.9

while left_motor.angle() < 3870:
    currentInput = color_sensor.reflection()
    error = currentInput - threshold

    inputDifference = currentInput - lastInput
    lastInput = currentInput

    # PID-Calculation
    p = Kp * error
    i = i + (Ki * error)
    d = d + (Kd * inputDifference)

    turn_rate = p + i + d
    robot.drive(speed, -turn_rate)
robot.stop()
time = watch.time()
ev3.screen.print(time)
wait(5000)
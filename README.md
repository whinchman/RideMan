Will's Biking Helper App.

# Problem
i, Will, am getting back into _casual_ cycling after a bunch years of being a coach potato. Every app in the app store is either A) overly complicated, B) Hard to Read, and/or C) full of ads. Being that this is all i do all day, we can bang out a simple application in under an hour and sideload it to the phone.
# High Concept
a K.I.S.S. android app for helping Will (me) with his bicycling.  Each feature is contained to a single screen, swiping left and right on the screen moves left and right in the circular queue. double tapping the screen uses TTS to read the current Speed, Distance Traveled, Heading, and altitude. 

1. Speedometer
2. Odometer
3. Compass
4. Altimeter
5. Candence Pulse (it's a metronome, but instead of BPM it's RPM)

# Design
Real K.I.S.S. principles here, everything should be BIG and LEGIBLE. Given it's for use outside in the sun, we should probably make the backgrounds dark and the Text Bright as possible (you know me I love me some neon/flourescent colors). We don't need fancy gauges, we don't need it to be cool. We need to be able to glance down, see the information, and back to focusing on not getting run over. 

# Screens
## Start
 - App landing screen, buttons for Settings and START RIDE
 - below that Shows debugging data (version, build, commit, etc)

## Settings
- Screen Display Order
- Unit Selection (Metric or American)
- Cadence Pulse - 1 rpm or 1/2 rpm (1 rpm = right foot, same position each pulse or 1/2 rpm = right foot down on pulse, left foot down on pulse)
- Cadence Pulse - set target RPM.
- save button

## Ride
- Swipe Left goes to the previous screen
- Swipe Right goes to the next screen
- Swiping left on the first screen loops to the last screen.
- swiping right on the last screen goes to the first screen.
- along the bottom of all screens, we show a paginator and a button for "end ride"
- see [Ride Sub Screens](#ride-sub-screens) for individual screens

## End Screen
- shows Total Time, Distance Traveled, Max Speed, Average Speed. 

# Ride Sub Screens
## Speedometer
- Displays Current Speed in MPH or Km/h

## Odometer
- Displays Current Distance Traveled in Miles or KM

## Compass
- Displays Current Heading in Degrees 

## Altimeter 
(not entirely sure if this one is possible, but i assume we can estimate it with decent accuracy using on phone GPS)
- Display current Altitude

## Cadence Pulse
- Displays Target Cadence
- 3 Buttons Speed Up, Play/Pause, Slow Down.
- when not paused, plays a click/pulse sound

# Data Collection
- Local data store that keeps ride information for each ride performed. 
- Not directly accessible in app by USER, but should be able to be grabbed off the phone and queried.
- should discuss this as it's architecture essential, but i'm not sure if we should use someting like SQL-Lite or just slam JSON files in a directory somewhere or if I should deploy some kind of endpoint (i really don't wanna do that). 

# Future Features
more complicated features. Not for the current build, but listed here for architecture planning purposes.
1. Tidal Music Integration
2. Gear selection helper (i.e. We're going up, time to shift to X)
3. Router Planner / Map
4. Sensor Integration (HeartRate, Cadence Sensor, etc.)
5. Personal Records! Time to beat! Etc.

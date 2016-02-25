# Roads

This is an Android application concept that I created the summer of 2015. It picks up the speed limit of the road via [OpenStreetMap](https://www.openstreetmap.org/), finds the speed that the user is travelling at, and provides some feedback as to if the user is speeding or not (just by emitting a beep). Users can adjust the speed limit should OSM provide inaccurate information.

The source code merely provides a proof-of-concept and in the future, I may expand upon this app. For now, though, I am not working on this project. There are a few items on the to-do list, though:

- Clean up files on exit
- Change speedlimittextview on swipe, and then confirm the variable is changed after
- Put everything in onLocationChanged() in async task
- Screens: speed, mark location, acceleration
- Need GPS connection, should have internet connection (but can work without)
- Make sure onCreate() and other methods work properly when user sleeps phone
- Implement an acceleration time measurement system
- Implement a distance traveled system (get lat/log and add to cummulative sum for total, do Euclidian distance for straight-line)
- Settings: choose between alarm or notification streams, switch light/dark option, keep screen on, set buffer speed
- Color code the speed
- Material theme/AMOLED theme
- Handle exiting from multitasking
- Explicitly credit APIs used

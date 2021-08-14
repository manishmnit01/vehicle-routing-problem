package com.google.ortools;

public class Vehicle extends Location {

	public Vehicle(String vehicleId, double latitude, double longitude, int startTimeMinutes, int endTimeMinutes) {
		super(vehicleId, latitude, longitude, startTimeMinutes, endTimeMinutes);
	}
}

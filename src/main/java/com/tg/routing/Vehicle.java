package com.tg.routing;

import java.util.List;

public class Vehicle extends Location {

	public String[] zones;

	public String[] orderTypes;

	public String firstName;

	public String lastName;

	public String mobileNumber;

	public Vehicle(String vehicleId, double latitude, double longitude, int startTimeMinutes, int endTimeMinutes) {
		super(vehicleId, latitude, longitude, startTimeMinutes, endTimeMinutes);
	}
}

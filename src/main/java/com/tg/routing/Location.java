package com.tg.routing;

public class Location {

	public String id;

	public double latitude;

	public double longitude;

	public int startTimeMinutes;

	public int endTimeMinutes;

	public Location(String id, double latitude, double longitude, int startTimeMinutes, int endTimeMinutes) {
		this.id = id;
		this.latitude = latitude;
		this.longitude = longitude;
		this.startTimeMinutes = startTimeMinutes;
		this.endTimeMinutes = endTimeMinutes;
	}
}

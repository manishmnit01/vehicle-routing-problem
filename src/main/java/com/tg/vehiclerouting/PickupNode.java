package com.tg.vehiclerouting;

public class PickupNode {

	public String orderId;

	public double latitude;

	public double longitude;

	public int startTimeMinutes;

	public int endTimeMinutes;

	public String zone;

	public String orderType;

	public int prevStatus;

	public PickupNode(String orderId, double latitude, double longitude, int startTimeMinutes, int endTimeMinutes) {
		this.orderId = orderId;
		this.latitude = latitude;
		this.longitude = longitude;
		this.startTimeMinutes = startTimeMinutes;
		this.endTimeMinutes = endTimeMinutes;
	}
}

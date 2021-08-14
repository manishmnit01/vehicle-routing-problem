package com.google.ortools;

public class PickupOrder extends Location {

	public PickupOrderStatus status = PickupOrderStatus.PENDING;

	public PickupOrder(String orderId, double latitude, double longitude, int startTime, int endTime) {
		super(orderId, latitude, longitude, startTime, endTime);
	}
}

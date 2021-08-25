package com.tg.routing;

public class PickupOrder extends Location {

	public PickupOrderStatus status = PickupOrderStatus.PENDING;

	public String zone;

	public String orderType;

	public int prevStatus;

	public PickupOrder(String orderId, double latitude, double longitude, int startTime, int endTime) {
		super(orderId, latitude, longitude, startTime, endTime);
	}
}

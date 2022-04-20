package com.tg.vehicleroutingv2;

public class PickupNode {

	public String orderId;

	public double latitude;

	public double longitude;

	public int startTimeMinutes;

	public int endTimeMinutes;

	public String zone;

	public int orderType = 1;

	public int prevStatus;

	public PickupNode(String orderId, double latitude, double longitude, int startTimeMinutes, int endTimeMinutes) {
		this.orderId = orderId;
		this.latitude = latitude;
		this.longitude = longitude;
		this.startTimeMinutes = startTimeMinutes;
		this.endTimeMinutes = endTimeMinutes;
	}

	public PickupNode(LgpOrder lgpOrder) {
		this.orderId = lgpOrder.orderId;
		this.latitude = lgpOrder.latitude;
		this.longitude = lgpOrder.longitude;
		this.startTimeMinutes = lgpOrder.startTimeMinutes;
		this.endTimeMinutes = lgpOrder.endTimeMinutes;
		this.zone = lgpOrder.zoneId;
		this.orderType = lgpOrder.orderType;
	}
}

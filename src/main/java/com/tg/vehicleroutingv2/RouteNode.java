package com.tg.vehicleroutingv2;

public class RouteNode {

	public int sequence;

	public String orderId;

	public long reachTime;

	public long leaveTime;

	public long startTimeMinutes;

	public long endTimeMinutes;

	public double latitude;

	public double longitude;

	public double distanceFromPrevNode;

	public double distanceSoFar;

	public boolean isBeyondSlotTime;

	public RouteNode(int sequence, String orderId, long reachTime, long leaveTime, double latitude, double longitude) {
		this.sequence = sequence;
		this.orderId = orderId;
		this.reachTime = reachTime;
		this.leaveTime = leaveTime;
		this.latitude = latitude;
		this.longitude = longitude;
	}
}

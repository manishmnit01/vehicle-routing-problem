package com.tg.vehiclerouting;

public class RouteNode {

	public int sequence;

	public String orderId;

	public long reachTime;

	public long leaveTime;

	public double latitude;

	public double longitude;

	public int distanceFromPrevNode;

	public int distanceSoFar;

	public RouteNode(int sequence, String orderId, long reachTime, long leaveTime, double latitude, double longitude) {
		this.sequence = sequence;
		this.orderId = orderId;
		this.reachTime = reachTime;
		this.leaveTime = leaveTime;
		this.latitude = latitude;
		this.longitude = longitude;
	}
}

package com.tg.routing;

public class RouteNode {

	public int sequence;

	public String orderId;

	public long reachTime;

	public long leaveTime;

	public double latitude;

	public double longitude;

	public double distance;

	public RouteNode(int sequence, String orderId, long reachTime, long leaveTime, double latitude, double longitude, double distance) {
		this.sequence = sequence;
		this.orderId = orderId;
		this.reachTime = reachTime;
		this.leaveTime = leaveTime;
		this.latitude = latitude;
		this.longitude = longitude;
		this.distance = distance;
	}

	@Override
	public String toString() {
		long reachHours= reachTime / 60;
		long reachMinutes = reachTime % 60;
		long leaveHours= leaveTime / 60;
		long leaveMinutes = leaveTime % 60;
		return "RouteNode{" +
				"orderId=" + orderId +
				" distance=" + distance +
				" reachTIme=" + reachHours + ":" + reachMinutes +
				" leaveTIme=" + leaveHours + ":" + leaveMinutes +
				'}';
	}
}

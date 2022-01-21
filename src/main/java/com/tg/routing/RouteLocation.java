package com.tg.routing;

public class RouteLocation {

	public int sequence;

	public String orderId;

	public long reachTime;

	public long leaveTime;

	public double latitude;

	public double longitude;

	public int distanceFromPrevNode;

	public int distanceSoFar;

	public RouteLocation(int sequence, String orderId, long reachTime, long leaveTime, double latitude, double longitude) {
		this.sequence = sequence;
		this.orderId = orderId;
		this.reachTime = reachTime;
		this.leaveTime = leaveTime;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	@Override
	public String toString() {
		long reachHours= reachTime / 60;
		long reachMinutes = reachTime % 60;
		long leaveHours= leaveTime / 60;
		long leaveMinutes = leaveTime % 60;
		return "RouteLocation{" +
				"orderId=" + orderId +
				" distance=" + distanceFromPrevNode +
				" reachTIme=" + reachHours + ":" + reachMinutes +
				" leaveTIme=" + leaveHours + ":" + leaveMinutes +
				'}';
	}
}

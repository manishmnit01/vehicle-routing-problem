package com.tg.vehicleroutingv2;

import java.util.List;

public class VehicleServiceUtil {

	private static final int VEHICLE_SPEED = 20;
	private static final int BUFFER_MINUTES_BEFORE = 10;
	private static final int BUFFER_MINUTES_AFTER = 30;

	public static final String TOM_TOM_URL = "https://api.tomtom.com/routing/matrix/2?key=D2yk1m47TnTygY9B82OGuikUHBbRR43K";

	public static long[][] computeTimeMatrix(List<PickupNode> PickupNodes) {
		int orderSize = PickupNodes.size();
		int totalSize = orderSize + 1;
		long[][] timeMatrix = new long[totalSize][totalSize];

		for (int fromNode = 0; fromNode < totalSize; ++fromNode) {
			for (int toNode = 0; toNode < totalSize; ++toNode) {
				if(fromNode == 0 || toNode == 0) {
					timeMatrix[fromNode][toNode] = 0;
				} else {
					PickupNode point1 = PickupNodes.get(fromNode - 1);
					PickupNode point2 = PickupNodes.get(toNode - 1);
					long distanceInKm = distance(point1.latitude, point1.longitude, point2.latitude, point2.longitude, "K");
					//long approaxDistance = (long) Math.ceil(distanceInKm);
					long approaximateTimeInMinutes = distanceInKm * 60 / VEHICLE_SPEED;
					timeMatrix[fromNode][toNode] = approaximateTimeInMinutes;
				}
			}
		}
		return timeMatrix;
	}

	public static long[][] computeDistanceMatrix(List<PickupNode> PickupNodes) {
		int orderSize = PickupNodes.size();
		int totalSize = orderSize + 1;
		long[][] distanceMatrix = new long[totalSize][totalSize];

		for (int fromNode = 0; fromNode < totalSize; ++fromNode) {
			for (int toNode = 0; toNode < totalSize; ++toNode) {
				if(fromNode == 0 || toNode == 0) {
					distanceMatrix[fromNode][toNode] = 0;
				} else {
					PickupNode point1 = PickupNodes.get(fromNode -1);
					PickupNode point2 = PickupNodes.get(toNode -1);
					double distanceInKm = distance(point1.latitude, point1.longitude, point2.latitude, point2.longitude, "K");
					long approaxDistance = Math.round(distanceInKm * 1000);
					distanceMatrix[fromNode][toNode] = approaxDistance;
				}
			}
		}

		//printMatrix(distanceMatrix);
		return distanceMatrix;
	}

	public static long[][] getTimeSlots(List<PickupNode> PickupNodes, int afterBuffer) {
		int orderSize = PickupNodes.size();
		int totalSize = orderSize+1;
		long[][] timeSlots = new long[totalSize][2];
		timeSlots[0][0] = 360; // 6AM
		timeSlots[0][1] = 360; // 6AM
		int beforeBuffer = 0;
		if(afterBuffer > 0) {
			beforeBuffer = BUFFER_MINUTES_BEFORE;
		}
		for (int i = 0; i < orderSize; ++i) {
			timeSlots[i + 1][0] = PickupNodes.get(i).startTimeMinutes - beforeBuffer;
			timeSlots[i + 1][1] = PickupNodes.get(i).endTimeMinutes + afterBuffer;
		}
		//printMatrix(timeSlots);
		return timeSlots;
	}

	public static long distance(double lat1, double lon1, double lat2, double lon2, String unit) {
		if ((lat1 == lat2) && (lon1 == lon2)) {
			return 0;
		}
		else {
			double theta = lon1 - lon2;
			double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
			dist = Math.acos(dist);
			dist = Math.toDegrees(dist);
			dist = dist * 60 * 1.1515;
			if (unit.equals("K")) {
				dist = dist * 1.609344;
			} else if (unit.equals("N")) {
				dist = dist * 0.8684;
			}
			dist = dist * 1.3;
			long distance = (long) Math.ceil(dist);
			return distance;
		}
	}

	public static void main(String args[]) {
		long distance = distance(28.4489425
				,77.0241726
				, 26.8925906, 75.8107421, "K");
		System.out.println(distance);
	}
}

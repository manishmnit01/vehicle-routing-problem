package com.tg.vehiclerouting;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class VehicleRoutingService {

	private static final int SERVICE_TIME = 20;
	private static final int VEHICLE_SPEED = 20;
	private static final int TIME_LIMIT_SECONDS = 5;
	private static final int BUFFER_MINUTES = 30;
	private static final int MAX_WAIT_TIME = 12*60;
	private static final int MAX_ONDUTY_TIME = 24*60;
	private static final int MAX_VEHICLES_PER_ZONE = 50;
	private static final String TIME_DIMENSION = "Time";
	private static final String DISTANCE_DIMENSION = "Distance";
	private static final Map<String, Integer> SERVICE_TIME_MAP;

	static {
		SERVICE_TIME_MAP = new HashMap<>();
		SERVICE_TIME_MAP.put("1", 15);
		SERVICE_TIME_MAP.put("2", 25);
		SERVICE_TIME_MAP.put("3", 20);
		SERVICE_TIME_MAP.put("4", 30);
	}

	public Map<String, VehicleRoutingData> createVehicleRouteData(MultipartFile file) throws IOException {

		Map<String, List<PickupNode>> zoneToPickupOrdersMap = new HashMap<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
		String line;
		while ((line = br.readLine()) != null && !line.isEmpty())   //returns a Boolean value
		{
			String[] orders = line.split(",");
			String id = orders[0];

			double latitute = Double.parseDouble(orders[1]);
			double longitude = Double.parseDouble(orders[2]);
			String[] startTimeSplit = orders[3].split(" ");
			int startHour = Integer.parseInt(startTimeSplit[0]);
			int startMinute = Integer.parseInt(startTimeSplit[1]);
			int startTimeMinutes = startHour * 60 + startMinute;
			String[] endTimeSplit = orders[4].split(" ");
			int endHour = Integer.parseInt(endTimeSplit[0]);
			int endMinute = Integer.parseInt(endTimeSplit[1]);
			int endTimeMinutes = endHour * 60 + endMinute;

			PickupNode pickupNode = new PickupNode(id, latitute, longitude, startTimeMinutes, endTimeMinutes);
			String zone = orders[5];
			pickupNode.zone = zone;
			pickupNode.orderType = orders[6];
			pickupNode.prevStatus = Integer.parseInt(orders[7]);
			zoneToPickupOrdersMap.computeIfAbsent(zone, k-> new ArrayList<>()).add(pickupNode);
		}
		br.close();

		Map<String, VehicleRoutingData> zoneToVehicleRoutingDataMap = new HashMap<>();
		for(Map.Entry<String, List<PickupNode>> entry : zoneToPickupOrdersMap.entrySet()) {
			String zone = entry.getKey();
			List<PickupNode> orderListForZone = entry.getValue();
			VehicleRoutingData data = new VehicleRoutingData();
			data.pickupNodes = orderListForZone;
			data.timeMatrix = computeTimeMatrix(orderListForZone);
			data.distanceMatrix = computeDistanceMatrix(orderListForZone);
			data.timeWindows = getTimeSlots(orderListForZone);
			data.orderCount = orderListForZone.size();
			data.vehicleCount = MAX_VEHICLES_PER_ZONE;
			data.depot = 0;

			zoneToVehicleRoutingDataMap.put(zone, data);
		}

		return zoneToVehicleRoutingDataMap;
	}

	public AllRoutesForZone createAllRoutesForZone(VehicleRoutingData data, String zone) {
		RoutingIndexManager manager = new RoutingIndexManager(data.timeWindows.length, data.vehicleCount, data.depot);
		RoutingModel routing = new RoutingModel(manager);
		Assignment solution = this.createRouteSolution(data, manager, routing);
		AllRoutesForZone allRoutesForZone = getAllRoutesForZone(data, routing, manager, solution);
		allRoutesForZone.zone = zone;
		return allRoutesForZone;
	}

	public Assignment createRouteSolution(VehicleRoutingData data, RoutingIndexManager manager, RoutingModel routing) {
		// Create and register a transit callback.
		int transitCallbackIndex =
				routing.registerTransitCallback((long fromIndex, long toIndex) -> {
					// Convert from routing variable Index to user NodeIndex.
					int fromNode = manager.indexToNode(fromIndex);
					int toNode = manager.indexToNode(toIndex);
					if(fromNode == toNode) {
						return 0;
					}
					else {
						return data.timeMatrix[fromNode][toNode];
					}
				});

		// Add Time constraint.
		routing.addDimension(transitCallbackIndex, // transit callback
				MAX_WAIT_TIME, // Maximum waiting time one vehicle can wait from one order to next
				MAX_ONDUTY_TIME, // Time before this vehicle has to reach the end depot.
				false, // start cumul to zero
				TIME_DIMENSION);
		RoutingDimension timeDimension = routing.getMutableDimension(TIME_DIMENSION);

		int distanceCallbackIndex =
				routing.registerTransitCallback((long fromIndex, long toIndex) -> {
					// Convert from routing variable Index to user NodeIndex.
					int fromNode = manager.indexToNode(fromIndex);
					int toNode = manager.indexToNode(toIndex);
					if(fromNode == toNode) {
						return 0;
					}
					else {
						return data.distanceMatrix[fromNode][toNode];
					}
				});
		// Define cost of each arc.
		routing.setArcCostEvaluatorOfAllVehicles(distanceCallbackIndex);
		routing.addDimension(distanceCallbackIndex, // transit callback
				0,
				3000, // Maximum distance by vehicle
				true, // start cumul to zero
				DISTANCE_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);
		distanceDimension.setGlobalSpanCostCoefficient(1000);

		routing.setFixedCostOfAllVehicles(1000);

		// Add time window constraints for each location except depot.
		for (int i = 1; i < data.timeWindows.length; ++i) {
			long index = manager.nodeToIndex(i);
			PickupNode pickupNode = data.pickupNodes.get(i - 1);
			timeDimension.cumulVar(index).setRange(data.timeWindows[i][0], data.timeWindows[i][1]);
			Integer serviceTimeForOrder = SERVICE_TIME_MAP.get(pickupNode.orderType);
			int serviceTime = serviceTimeForOrder != null ? serviceTimeForOrder : SERVICE_TIME;
			timeDimension.slackVar(index).setRange(serviceTime, MAX_WAIT_TIME);
		}
		// Add time window constraints for each vehicle start node.
		for (int i = 0; i < data.vehicleCount; ++i) {
			long startIndex = routing.start(i);
			timeDimension.cumulVar(startIndex).setRange(data.timeWindows[0][0], data.timeWindows[0][1]);
		}

		// Instantiate route start and end times to produce feasible times.
		for (int i = 0; i < data.vehicleCount; ++i) {
			routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.start(i)));
			routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.end(i)));
		}

		RoutingSearchParameters searchParameters =
				main.defaultRoutingSearchParameters()
						.toBuilder()
						.setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
						.setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
						.setTimeLimit(Duration.newBuilder().setSeconds(TIME_LIMIT_SECONDS).build())
						.build();

		Assignment solution = routing.solveWithParameters(searchParameters);
		return solution;
	}

	public AllRoutesForZone getAllRoutesForZone(
			VehicleRoutingData data, RoutingModel routing, RoutingIndexManager manager, Assignment solution) {
		// Solution cost.
		if(solution == null) {
			throw new RuntimeException("Route is not possible within given time slots");
		}

		// Inspect solution.
		RoutingDimension timeDimension = routing.getMutableDimension(TIME_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);
		long totalTime = 0;
		List<List<RouteNode>> allRoutes = new ArrayList<>();
		for (int i = 0; i < data.vehicleCount; ++i) {
			List<RouteNode> route = new ArrayList<>();
			long index = routing.start(i);
			IntVar startTimeVar = timeDimension.cumulVar(index);
			int startPointIndex = manager.indexToNode(index);
			index = solution.value(routing.nextVar(index));
			PickupNode prevNode = null;
			PickupNode currentNode = null;

			while (!routing.isEnd(index)) {
				IntVar timeVar = timeDimension.cumulVar(index);
				int orderIndex = manager.indexToNode(index);
				prevNode = currentNode;
				currentNode = data.pickupNodes.get(orderIndex - 1);
				RouteNode routeNode = new RouteNode(orderIndex, currentNode.orderId, solution.min(timeVar), solution.max(timeVar), currentNode.latitude, currentNode.longitude);
				if(prevNode != null){
					routeNode.distanceFromPrevNode = (int) distance(prevNode.latitude, prevNode.longitude, currentNode.latitude, currentNode.longitude, "K");
				}
				routeNode.distanceSoFar = (int) solution.min(distanceDimension.cumulVar(index));
				route.add(routeNode);
				index = solution.value(routing.nextVar(index));
			}
			if(!route.isEmpty()) {
				allRoutes.add(route);
			}
		}

		AllRoutesForZone allRoutesForZone = new AllRoutesForZone();
		allRoutesForZone.allRoutes = allRoutes;
		return allRoutesForZone;
	}

	private static long[][] computeTimeMatrix(List<PickupNode> PickupNodes) {
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

	private static long[][] computeDistanceMatrix(List<PickupNode> PickupNodes) {
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
					long approaxDistance = Math.round(distanceInKm);
					distanceMatrix[fromNode][toNode] = approaxDistance;
				}
			}
		}

		//printMatrix(distanceMatrix);
		return distanceMatrix;
	}

	private static long[][] getTimeSlots(List<PickupNode> PickupNodes) {
		int orderSize = PickupNodes.size();
		int totalSize = orderSize+1;
		long[][] timeSlots = new long[totalSize][2];
		timeSlots[0][0] = 360; // 6AM
		timeSlots[0][1] = 360; // 6AM
		for (int i = 0; i < orderSize; ++i) {
			timeSlots[i + 1][0] = PickupNodes.get(i).startTimeMinutes - BUFFER_MINUTES;
			timeSlots[i + 1][1] = PickupNodes.get(i).endTimeMinutes + BUFFER_MINUTES;
		}
		//printMatrix(timeSlots);
		return timeSlots;
	}

	private static long distance(double lat1, double lon1, double lat2, double lon2, String unit) {
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
}


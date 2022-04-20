package com.tg.vehicleroutingv2;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.tg.tomtom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VehicleRoutingService {

	private static final int SERVICE_TIME = 20;
	private static final int VEHICLE_SPEED = 20;
	private static final int TIME_LIMIT_SECONDS = 1;
	private static final int BUFFER_MINUTES_BEFORE = 10;
	private static final int BUFFER_MINUTES_AFTER = 30;
	private static final int MAX_WAIT_TIME = 12*60;
	private static final int MAX_ONDUTY_TIME = 24*60;
	private static final int MAX_VEHICLES_PER_ZONE = 50;
	private static final String TIME_DIMENSION = "Time";
	private static final String DISTANCE_DIMENSION = "Distance";
	private static final Map<Integer, Integer> SERVICE_TIME_MAP;

	static {
		SERVICE_TIME_MAP = new HashMap<>();
		SERVICE_TIME_MAP.put(1, 20);
		SERVICE_TIME_MAP.put(2, 30);
		SERVICE_TIME_MAP.put(3, 20);
		SERVICE_TIME_MAP.put(4, 40);
	}

	@Autowired
	private VehicleRoutingRepository vehicleRoutingRepository;

	@Autowired
	private RestTemplate restTemplate;

	public Map<String, VehicleRoutingData> createVehicleRouteData(MultipartFile file, int buffer, boolean useDistanceApi) throws IOException {

		Map<String, List<PickupNode>> zoneToPickupOrdersMap = new HashMap<>();
		Map<String, Integer> zoneVehicleCountMap = new HashMap<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
		String line;
		while ((line = br.readLine()) != null && !line.isEmpty())   //returns a Boolean value
		{
			String[] orders = line.split(",");
			if(orders.length < 8) {
				continue;
			}
			String orderId = orders[0];
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

			PickupNode pickupNode = new PickupNode(orderId, latitute, longitude, startTimeMinutes, endTimeMinutes);
			String zone = orders[5];
			pickupNode.zone = zone;
			pickupNode.orderType = Integer.parseInt(orders[6]);
			pickupNode.prevStatus = Integer.parseInt(orders[7]);
			int maxVehicleInZone = MAX_VEHICLES_PER_ZONE;
			if(orders.length > 8 ) {
				maxVehicleInZone = Integer.parseInt(orders[8]);
			}
			zoneVehicleCountMap.put(zone, maxVehicleInZone);
			zoneToPickupOrdersMap.computeIfAbsent(zone, k-> new ArrayList<>()).add(pickupNode);
		}
		br.close();

		Map<String, VehicleRoutingData> zoneToVehicleRoutingDataMap = new HashMap<>();
		for(Map.Entry<String, List<PickupNode>> entry : zoneToPickupOrdersMap.entrySet()) {
			String zone = entry.getKey();
			List<PickupNode> orderListForZone = entry.getValue();
			int vehicleCount = zoneVehicleCountMap.get(zone);
			VehicleRoutingData data = convertPickupOrdersToRoutingData(orderListForZone, vehicleCount, buffer, useDistanceApi);
			zoneToVehicleRoutingDataMap.put(zone, data);
		}

		return zoneToVehicleRoutingDataMap;
	}

	public VehicleRoutingData convertPickupOrdersToRoutingData(List<PickupNode> orderList, int vehicleCount,int buffer, boolean useDistanceApi) {
		VehicleRoutingData data = new VehicleRoutingData();
		data.pickupNodes = orderList;
		DistanceTimeMatrix distanceTimeMatrix = this.computeDistanceTimeMatrix(orderList, useDistanceApi);
		data.distanceMatrix = distanceTimeMatrix.distanceMatrix;
		data.timeMatrix = distanceTimeMatrix.timeMatrix;
		data.timeWindows = VehicleServiceUtil.getTimeSlots(orderList, buffer);
		data.orderCount = orderList.size();
		data.vehicleCount = vehicleCount;
		data.depot = 0;
		return data;
	}

	public OrderAssignmentData generateRoutesForAllZones(Map<String, VehicleRoutingData> zoneToVehicleRoutingDataMap, String assignmentId, int maxDistance) {

		OrderAssignmentData orderAssignment = new OrderAssignmentData();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = "COMPLETED";
		orderAssignment.failedMessage = null;

		int totalRoutes = 0;
		List<ZoneWiseRoutes> allRoutes = new ArrayList<>();
		try {
			int routeId = 1;
			for (Map.Entry<String, VehicleRoutingData> entry : zoneToVehicleRoutingDataMap.entrySet()) {
				String zone = entry.getKey();
				VehicleRoutingData vehicleRoutingData = entry.getValue();
				ZoneWiseRoutes zoneWiseRoutes = this.createAllRoutesForZone(vehicleRoutingData, zone, maxDistance);
				totalRoutes += zoneWiseRoutes.allRoutesForZone.size();
				vehicleRoutingRepository.updateAllOrdersForZone(zoneWiseRoutes, routeId);
				routeId = routeId + zoneWiseRoutes.allRoutesForZone.size();
				allRoutes.add(zoneWiseRoutes);
			}
			orderAssignment.totalRoutes = totalRoutes;
		} catch (Exception e) {
			orderAssignment.status = "FAILED";
			orderAssignment.failedMessage = e.getMessage();
			e.printStackTrace();
		}

		OrderAssignmentData orderAssignmentUpdated = vehicleRoutingRepository.updateOrderAssignment(orderAssignment);
		orderAssignmentUpdated.allRoutesForCity = allRoutes;
		return orderAssignmentUpdated;
	}

	public ZoneWiseRoutes createAllRoutesForZone(VehicleRoutingData data, String zone, int maxDistance) {
		RoutingIndexManager manager = new RoutingIndexManager(data.timeWindows.length, data.vehicleCount, data.depot);
		RoutingModel routing = new RoutingModel(manager);
		int vehicleCost = maxDistance > 0 ? maxDistance : 8000;
		Assignment solution = this.createRouteSolution(data, manager, routing, maxDistance, vehicleCost);
		if(solution == null && data.vehicleCount < MAX_VEHICLES_PER_ZONE) {
			data.vehicleCount = MAX_VEHICLES_PER_ZONE;
			manager = new RoutingIndexManager(data.timeWindows.length, data.vehicleCount, data.depot);
			routing = new RoutingModel(manager);
			solution = this.createRouteSolution(data, manager, routing, maxDistance, 100000);
		}
		if(solution == null) {
			throw new RuntimeException("Route is not possible within given time slots");
		}
		ZoneWiseRoutes zoneWiseRoutes = this.getAllRoutesForZone(data, routing, manager, solution);
		zoneWiseRoutes.zone = zone;
		return zoneWiseRoutes;
	}

	public List<RouteNode> createOneRouteForOrders(VehicleRoutingData data, int maxDistance) {
		RoutingIndexManager manager = new RoutingIndexManager(data.timeWindows.length, data.vehicleCount, data.depot);
		RoutingModel routing = new RoutingModel(manager);
		Assignment solution = this.createRouteSolution(data, manager, routing, maxDistance, 0);
		if(solution == null) {
			throw new RuntimeException("Route is not possible within given time slots");
		}
		List<RouteNode> route = this.getOneRouteOfVehicle(data, routing, manager, solution, 0);
		return route;
	}

	public OrderAssignmentData verifyOrderAssignment(String assignmentId) {
		//String assignmentId = UUID.randomUUID().toString();
		OrderAssignmentData orderAssignment = new OrderAssignmentData();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = "PROCESSING";
		return vehicleRoutingRepository.updateOrderAssignment(orderAssignment);
	}

	private Assignment createRouteSolution(VehicleRoutingData data, RoutingIndexManager manager,
										   RoutingModel routing, int maxDistance, int vehicleCost) {
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
						long distance = data.distanceMatrix[fromNode][toNode];
						if( distance <= maxDistance) {
							return distance;
						} else {
							long timeDifference = Math.abs(data.timeWindows[toNode][1]-data.timeWindows[fromNode][1]);
							if(timeDifference >= 60) {
								return distance;
							} else {
								return 100000 + distance;
							}
						}
					}
				});
		// Define cost of each arc.
		routing.setArcCostEvaluatorOfAllVehicles(distanceCallbackIndex);
		routing.addDimension(distanceCallbackIndex, // transit callback
				0,
				100000, // Maximum distance by vehicle
				true, // start cumul to zero
				DISTANCE_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);

		// Since we are taking distance in meters no need to set span coefficient.
		// distanceDimension.setGlobalSpanCostCoefficient(100);

		routing.setFixedCostOfAllVehicles(vehicleCost);

		int sameHouseOrder = 1;
		// Add time window constraints for each location except depot.
		for (int i = 1; i < data.timeWindows.length; ++i) {
			long index = manager.nodeToIndex(i);
			PickupNode pickupNode = data.pickupNodes.get(i - 1);
			timeDimension.cumulVar(index).setRange(data.timeWindows[i][0], data.timeWindows[i][1]);
			Integer serviceTimeForOrder = SERVICE_TIME_MAP.get(pickupNode.orderType);
			int serviceTime = serviceTimeForOrder != null ? serviceTimeForOrder : SERVICE_TIME;
			serviceTime = sameHouseOrder * serviceTime;

			// If next pickup is in same house, do not set any service time to avoid
			// assigning different vehicles for same house.
			if(i == data.distanceMatrix.length-1 || data.distanceMatrix[i][i+1] > 0) {
				timeDimension.slackVar(index).setRange(serviceTime, MAX_WAIT_TIME);
				sameHouseOrder = 1;
			} else {
				sameHouseOrder++;
			}
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

	private ZoneWiseRoutes getAllRoutesForZone(
			VehicleRoutingData data, RoutingModel routing, RoutingIndexManager manager, Assignment solution) {
		RoutingDimension timeDimension = routing.getMutableDimension(TIME_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);
		List<List<RouteNode>> allRoutes = new ArrayList<>();
		for (int vehicleIndex = 0; vehicleIndex < data.vehicleCount; vehicleIndex++) {
			List<RouteNode> route = this.getOneRouteOfVehicle(data, routing, manager, solution, vehicleIndex);
			if(!route.isEmpty()) {
				allRoutes.add(route);
			}
		}

		ZoneWiseRoutes zoneWiseRoutes = new ZoneWiseRoutes();
		zoneWiseRoutes.allRoutesForZone = allRoutes;
		return zoneWiseRoutes;
	}

	public List<RouteNode> getOneRouteOfVehicle(VehicleRoutingData data, RoutingModel routing, RoutingIndexManager manager, Assignment solution, int vehicleIndex) {

		RoutingDimension timeDimension = routing.getMutableDimension(TIME_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);

		List<RouteNode> route = new ArrayList<>();
		long index = routing.start(vehicleIndex);
		//IntVar startTimeVar = timeDimension.cumulVar(index);
		//int startPointIndex = manager.indexToNode(index);
		index = solution.value(routing.nextVar(index));
		PickupNode prevNode = null;
		PickupNode currentNode = null;
		int prevIndex = -1;
		int currentIndex = 0;

		while (!routing.isEnd(index)) {
			IntVar timeVar = timeDimension.cumulVar(index);
			prevIndex = currentIndex;
			currentIndex = manager.indexToNode(index);
			prevNode = currentNode;
			currentNode = data.pickupNodes.get(currentIndex - 1);
			RouteNode routeNode = new RouteNode(currentIndex, currentNode.orderId, solution.min(timeVar), solution.max(timeVar), currentNode.latitude, currentNode.longitude);
			long distanceFromPrevNode = data.distanceMatrix[prevIndex] [currentIndex];
			routeNode.distanceFromPrevNode = (distanceFromPrevNode * 1.0) / 1000;
			long distanceSoFar = solution.min(distanceDimension.cumulVar(index));
			routeNode.distanceSoFar = (distanceSoFar * 1.0) / 1000;
			if (routeNode.reachTime > currentNode.endTimeMinutes) {
				routeNode.isBeyondSlotTime = true;
			}
			routeNode.startTimeMinutes = data.timeWindows[currentIndex][0];
			routeNode.endTimeMinutes = data.timeWindows[currentIndex][1];
			route.add(routeNode);

			index = solution.value(routing.nextVar(index));
		}

		return route;
	}

	private DistanceTimeMatrix computeDistanceTimeMatrix(List<PickupNode> pickupNodes, boolean useDistanceApi) {
		int orderSize = pickupNodes.size();
		int depotPlusOrderSize = orderSize + 1;
		long[][] timeMatrix = new long[depotPlusOrderSize][depotPlusOrderSize];
		long[][] distanceMatrix = new long[depotPlusOrderSize][depotPlusOrderSize];

		if (useDistanceApi) {
			/*List<SinglePoint> allPoints = pickupNodes.stream().map(node -> new SinglePoint(new Point(node.latitude, node.longitude))).collect(Collectors.toList());
			MatrixApiRequest matrixApiRequest = new MatrixApiRequest(allPoints, allPoints);
			MatrixApiResponse matrixApiResponse = restTemplate.postForObject(VehicleServiceUtil.TOM_TOM_URL, matrixApiRequest, MatrixApiResponse.class);

			if (matrixApiResponse == null) {
				distanceMatrix = VehicleServiceUtil.computeDistanceMatrix(pickupNodes);
				timeMatrix = VehicleServiceUtil.computeTimeMatrix(pickupNodes);
				return new DistanceTimeMatrix(distanceMatrix, timeMatrix);
			}

			for (TwoPointRoute twoPointRoute : matrixApiResponse.data) {
				int distanceInMeters = twoPointRoute.routeSummary.lengthInMeters;
				int timeInSeconds = twoPointRoute.routeSummary.travelTimeInSeconds;
				int timeInMinutes = timeInSeconds / 60;

				if (distanceInMeters <= 100) {
					distanceInMeters = 0;
					timeInMinutes = 0;
				}

				distanceMatrix[twoPointRoute.originIndex + 1][twoPointRoute.destinationIndex + 1] = distanceInMeters;
				timeMatrix[twoPointRoute.originIndex + 1][twoPointRoute.destinationIndex + 1] = timeInMinutes;
			}*/

			int originSize = 200/orderSize;
			originSize = originSize <= 10 ? originSize : 10;

			int n = orderSize / originSize;
			if (orderSize % originSize != 0) {
				n = n + 1;
			}

			for (int i = 0; i < n; i++) {
				List<SinglePoint> originPoints = new ArrayList<>(originSize);
				for(int j=0; j<originSize && i*originSize+j < orderSize; j++) {
					PickupNode pickupNode = pickupNodes.get(i*originSize+j);
					SinglePoint singlePoint = new SinglePoint(new Point(pickupNode.latitude, pickupNode.longitude));
					originPoints.add(singlePoint);
				}

				List<SinglePoint> destinationPoints = new ArrayList<>(orderSize);
				for (int j = 0; j < orderSize; j++) {
					PickupNode pickupNode = pickupNodes.get(j);
					SinglePoint singlePoint = new SinglePoint(new Point(pickupNode.latitude, pickupNode.longitude));
					destinationPoints.add(singlePoint);
				}
				MatrixApiRequest matrixApiRequest = new MatrixApiRequest(originPoints, destinationPoints);
				MatrixApiResponse matrixApiResponse = restTemplate.postForObject(VehicleServiceUtil.TOM_TOM_URL, matrixApiRequest, MatrixApiResponse.class);

				if (matrixApiResponse == null) {
					throw new RuntimeException("No response from Tom Tom API");
				}

				for (TwoPointRoute twoPointRoute : matrixApiResponse.data) {
					int distanceInMeters = twoPointRoute.routeSummary.lengthInMeters;
					int timeInSeconds = twoPointRoute.routeSummary.travelTimeInSeconds;
					int timeInMinutes = timeInSeconds / 60;

					if (distanceInMeters <= 100) {
						distanceInMeters = 0;
						timeInMinutes = 0;
					}
					int fromIndex = (i * originSize) + (twoPointRoute.originIndex + 1);
					int toIndex = (twoPointRoute.destinationIndex + 1);
					distanceMatrix[fromIndex][toIndex] = distanceInMeters;
					timeMatrix[fromIndex][toIndex] = timeInMinutes;
				}
			}
		} else {
			distanceMatrix = VehicleServiceUtil.computeDistanceMatrix(pickupNodes);
			timeMatrix = VehicleServiceUtil.computeTimeMatrix(pickupNodes);
		}
		return new DistanceTimeMatrix(distanceMatrix, timeMatrix);
	}
}


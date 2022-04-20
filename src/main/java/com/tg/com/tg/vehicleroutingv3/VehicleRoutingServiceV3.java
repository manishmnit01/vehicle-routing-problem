package com.tg.com.tg.vehicleroutingv3;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.tg.vehicleroutingv2.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class VehicleRoutingServiceV3 {

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
	private static final Map<String, Integer> SERVICE_TIME_MAP;

	static {
		SERVICE_TIME_MAP = new HashMap<>();
		SERVICE_TIME_MAP.put("1", 20);
		SERVICE_TIME_MAP.put("2", 30);
		SERVICE_TIME_MAP.put("3", 20);
		SERVICE_TIME_MAP.put("4", 40);
	}

	@Autowired
	private VehicleRoutingRepository vehicleRoutingRepository;

	public OrderAssignmentData verifyOrderAssignment(String assignmentId) {
		//String assignmentId = UUID.randomUUID().toString();
		OrderAssignmentData orderAssignment = new OrderAssignmentData();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = "PROCESSING";
		return vehicleRoutingRepository.updateOrderAssignment(orderAssignment);
	}

	public VehicleRoutingData createVehicleRouteData(MultipartFile file, int buffer, int vehicleCount) throws IOException {

		List<PickupNode> pickupOrdersList = new ArrayList<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
		String line;
		Set<String> allZones = new HashSet<>();
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
			allZones.add(zone);
			pickupNode.zone = zone;
			pickupNode.orderType = Integer.parseInt(orders[6]);
			pickupNode.prevStatus = Integer.parseInt(orders[7]);

			pickupOrdersList.add(pickupNode);
		}
		br.close();

		VehicleRoutingData data = new VehicleRoutingData();
		data.pickupNodes = pickupOrdersList;
		data.timeMatrix = VehicleServiceUtil.computeTimeMatrix(pickupOrdersList);
		data.distanceMatrix = VehicleServiceUtil.computeDistanceMatrix(pickupOrdersList);
		data.timeWindows = VehicleServiceUtil.getTimeSlots(pickupOrdersList, buffer);
		data.orderCount = pickupOrdersList.size();
		data.vehicleCount = vehicleCount;
		data.depot = 0;
		data.allZones = allZones;

		return data;
	}

	public OrderAssignmentData generateRoutesForCity(VehicleRoutingData vehicleRoutingData, String assignmentId) {

		OrderAssignmentData orderAssignment = new OrderAssignmentData();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = "COMPLETED";
		orderAssignment.failedMessage = null;

		List<List<RouteNode>> allRoutes = new ArrayList<>();
		try {
			allRoutes = this.createAllRoutesForCity(vehicleRoutingData);
			vehicleRoutingRepository.updateAllOrdersForCity(allRoutes);
			orderAssignment.totalRoutes = allRoutes.size();

		} catch (Exception e) {
			orderAssignment.status = "FAILED";
			orderAssignment.failedMessage = e.getMessage();
			e.printStackTrace();
		}

		OrderAssignmentData orderAssignmentUpdated = vehicleRoutingRepository.updateOrderAssignment(orderAssignment);
		orderAssignmentUpdated.allRoutes = allRoutes;
		return orderAssignmentUpdated;
	}

	public List<List<RouteNode>> createAllRoutesForCity(VehicleRoutingData data) {
		RoutingIndexManager manager = new RoutingIndexManager(data.timeWindows.length, data.vehicleCount, data.depot);
		RoutingModel routing = new RoutingModel(manager);
		Assignment solution = this.createRouteSolution(data, manager, routing);
		List<List<RouteNode>> cityWiseRoutes = this.getAllRoutesForCity(data, routing, manager, solution);
		return cityWiseRoutes;
	}

	private Assignment createRouteSolution(VehicleRoutingData data, RoutingIndexManager manager, RoutingModel routing) {
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
				500, // Maximum distance by vehicle
				true, // start cumul to zero
				DISTANCE_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);
		distanceDimension.setGlobalSpanCostCoefficient(100);

		// Put restrictions for zone
		for (String zone : data.allZones) {
			int zoneCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
				int fromNode = manager.indexToNode(fromIndex);
				if (fromNode == 0) {
					return 0;
				}
				String zoneOfOrder = data.pickupNodes.get(((int)fromNode) -1).zone;
				if (zone.equals(zoneOfOrder)) {
					return 1;
				}
				return 0;
			});
			routing.addDimension(zoneCallbackIndex, 0, // null capacity slack
					100, // vehicle maximum capacities
					true, // start cumul to zero
					zone);
		}

		/*Solver solver = routing.solver();
		for (int i = 0; i < data.vehicleCount; i++)
		{
			IntVar[] array = new IntVar[data.allZones.size()];
			int x =0;
			for (String zone : data.allZones) {
				RoutingDimension zoneDimension = routing.getMutableDimension(zone);
				IntVar intVar = solver.makeIsGreaterCstVar(zoneDimension.cumulVar(routing.end(i)), 0);
				array[x++] = intVar;
			}
			solver.addConstraint(solver.makeSumLessOrEqual(array, 1));
		}*/

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

	private List<List<RouteNode>> getAllRoutesForCity(
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
					routeNode.distanceFromPrevNode = (int) VehicleServiceUtil.distance(prevNode.latitude, prevNode.longitude, currentNode.latitude, currentNode.longitude, "K");
				}
				routeNode.distanceSoFar = (int) solution.min(distanceDimension.cumulVar(index));
				if (routeNode.reachTime > currentNode.endTimeMinutes) {
					routeNode.isBeyondSlotTime = true;
				}
				route.add(routeNode);

				index = solution.value(routing.nextVar(index));
			}
			if(!route.isEmpty()) {
				allRoutes.add(route);
			}
		}

		return allRoutes;
	}
}

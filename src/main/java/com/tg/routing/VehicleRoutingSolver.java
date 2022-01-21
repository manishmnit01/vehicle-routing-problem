package com.tg.routing;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VehicleRoutingSolver {

	private static final int SERVICE_TIME = 20;
	private static final int VEHICLE_SPEED = 20;
	private static final int TIME_LIMIT_SECONDS = 60;
	private static final int BUFFER_MINUTES = 0;
	private static final int MAX_WAIT_TIME = 12*60;
	private static final int MAX_ONDUTY_TIME = 24*60;
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

	@Autowired
	private LgpOrderRepository lgpOrderRepository;

	public VehicleRoutingInputData createVehicleRouteData(MultipartFile file) throws IOException {
		VehicleRoutingInputData data = new VehicleRoutingInputData();

		List<PickupOrder> pickupOrderList = data.pickupOrders;
		List<Vehicle> vehicleList = data.vehicles;
		boolean orderFlag = true;
		Map<String, HashSet<Integer>> zoneToOrderSeqMap = new HashMap<>();
		Map<String, HashSet<Integer>> zoneToVehicleSeqMap = new HashMap<>();

		Map<String, HashSet<Integer>> orderTypeToOrderSeqMap = new HashMap<>();
		Map<String, HashSet<Integer>> orderTypeToVehicleSeqMap = new HashMap<>();
		int orderCount = 0;
		int vehicleCount = 0;

		BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
		String line;
		while ((line = br.readLine()) != null && !line.isEmpty())   //returns a Boolean value
		{
			String[] orders = line.split(",");
			String id = orders[0];
			if(id.equals("END")) {
				orderFlag = false;
				continue;
			}

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

			if(orderFlag) {
				PickupOrder pickupOrder = new PickupOrder(id, latitute, longitude, startTimeMinutes, endTimeMinutes);
				String zone = orders[5];
				pickupOrder.zone = zone;
				String orderType = orders[6];
				pickupOrder.orderType = orderType;
				pickupOrder.prevStatus = Integer.parseInt(orders[7]);
				pickupOrderList.add(pickupOrder);

				HashSet<Integer> orderSeqSetForZone = zoneToOrderSeqMap.get(zone);
				if (orderSeqSetForZone == null) {
					orderSeqSetForZone = new HashSet<>();
					zoneToOrderSeqMap.put(zone, orderSeqSetForZone);
				}
				orderSeqSetForZone.add(orderCount);

				HashSet<Integer> orderSeqSetForType = orderTypeToOrderSeqMap.get(orderType);
				if (orderSeqSetForType == null) {
					orderSeqSetForType = new HashSet<>();
					orderTypeToOrderSeqMap.put(orderType, orderSeqSetForType);
				}
				orderSeqSetForType.add(orderCount);
				orderCount++;
			}
			else {
				Vehicle vehicle = new Vehicle(id, latitute, longitude, startTimeMinutes, endTimeMinutes);
				String[] zones = orders[5].split("#");
				vehicle.zones = zones;
				String[] orderTypes = orders[6].split("#");
				vehicle.orderTypes = orderTypes;
				vehicle.firstName = orders[8];
				vehicle.lastName = orders[9];
				vehicle.mobileNumber = orders[10];
				vehicleList.add(vehicle);
				data.vehiclesMap.put(id, vehicle);

				for (String zone : zones) {
					HashSet<Integer> vehicleSeqSetForZone = zoneToVehicleSeqMap.get(zone);
					if (vehicleSeqSetForZone == null) {
						vehicleSeqSetForZone = new HashSet<>();
						zoneToVehicleSeqMap.put(zone, vehicleSeqSetForZone);
					}
					vehicleSeqSetForZone.add(vehicleCount);
				}

				for (String orderType : orderTypes) {
					HashSet<Integer> vehicleSeqSetForType = orderTypeToVehicleSeqMap.get(orderType);
					if (vehicleSeqSetForType == null) {
						vehicleSeqSetForType = new HashSet<>();
						orderTypeToVehicleSeqMap.put(orderType, vehicleSeqSetForType);
					}
					vehicleSeqSetForType.add(vehicleCount);
				}
				vehicleCount++;
			}
		}
		br.close();

		data.allZones.addAll(zoneToVehicleSeqMap.keySet());
		data.allZones.addAll(zoneToOrderSeqMap.keySet());

		data.allOrderTypes.addAll(orderTypeToVehicleSeqMap.keySet());
		data.allOrderTypes.addAll(orderTypeToOrderSeqMap.keySet());

		for (String orderType : data.allOrderTypes) {
			long[] orderSeqArrayForOrderType = new long[orderCount + vehicleCount];
			data.orderTypePresentInOrders.put(orderType, orderSeqArrayForOrderType);
			Set<Integer> ordersForOrderType = orderTypeToOrderSeqMap.get(orderType);
			if(ordersForOrderType == null) {
				ordersForOrderType = new HashSet<>();
			}

			for (int i=0; i < vehicleCount; i++) {
				orderSeqArrayForOrderType[i] = 0;
			}

			for (int i=0; i < orderCount; i++) {
				if (ordersForOrderType.contains(i)) {
					orderSeqArrayForOrderType[i + vehicleCount] = 1;
				} else {
					orderSeqArrayForOrderType[i + vehicleCount] = 0;
				}
			}

			long[] vehicleSeqArrayForOrderType = new long[vehicleCount];
			data.vehiclesServingOrderType.put(orderType, vehicleSeqArrayForOrderType);
			Set<Integer> vehiclesForOrderType = orderTypeToVehicleSeqMap.get(orderType);
			if(vehiclesForOrderType == null) {
				vehiclesForOrderType = new HashSet<>();
			}
			for (int i=0; i < vehicleCount; i++) {
				if (vehiclesForOrderType.contains(i)) {
					vehicleSeqArrayForOrderType[i] = 100;
				} else {
					vehicleSeqArrayForOrderType[i] = 0;
				}
			}
		}

		for (String zone : data.allZones) {
			long[] orderSeqArrayForZone = new long[orderCount + vehicleCount];
			data.zonePresentInOrders.put(zone, orderSeqArrayForZone);
			Set<Integer> ordersForZone = zoneToOrderSeqMap.get(zone);
			if(ordersForZone == null) {
				ordersForZone = new HashSet<>();
			}

			for (int i=0; i < vehicleCount; i++) {
				orderSeqArrayForZone[i] = 0;
			}

			for (int i=0; i < orderCount; i++) {
				if (ordersForZone.contains(i)) {
					orderSeqArrayForZone[i + vehicleCount] = 1;
				} else {
					orderSeqArrayForZone[i + vehicleCount] = 0;
				}
			}

			long[] vehicleSeqArrayForZone = new long[vehicleCount];
			data.vehiclesServingZone.put(zone, vehicleSeqArrayForZone);
			Set<Integer> vehiclesForZone = zoneToVehicleSeqMap.get(zone);
			if(vehiclesForZone == null) {
				vehiclesForZone = new HashSet<>();
			}
			for (int i=0; i < vehicleCount; i++) {
				if (vehiclesForZone.contains(i)) {
					vehicleSeqArrayForZone[i] = 100;
				} else {
					vehicleSeqArrayForZone[i] = 0;
				}
			}
		}

		data.timeMatrix = computeTimeMatrix(pickupOrderList, vehicleList);
		data.distanceMatrix = computeDistanceMatrix(pickupOrderList, vehicleList);
		data.timeWindows = getTimeSlots(pickupOrderList, vehicleList);
		data.vehicleCount = vehicleList.size();
		data.orderCount = pickupOrderList.size();
		data.starts = new int[data.vehicleCount];
		for (int i = 0; i< data.vehicleCount; i++) {
			data.starts[i] = i;
		}
		data.ends = new int[data.vehicleCount];
		for (int i = 0; i< data.vehicleCount; i++) {
			data.ends[i] = i;
		}
		data.depot = 0;
		return data;
	}

	public Assignment createRouteSolution(VehicleRoutingInputData data, RoutingIndexManager manager, RoutingModel routing) {
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

		// Add condition for minimum distance travelled by each vehicle.
		/*for (int i = 0; i < data.vehicleCount; ++i) {
			long endIndex = routing.end(i);
			distanceDimension.setCumulVarSoftLowerBound(endIndex, 5, 100000);
			//distanceDimension.cumulVar(endIndex).removeInterval(0, 5);
		}*/

		// Put restrictions for order type
		for (String orderType : data.allOrderTypes) {
			int orderTypeCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
				int fromNode = manager.indexToNode(fromIndex);
				return data.orderTypePresentInOrders.get(orderType)[fromNode];
			});
			routing.addDimensionWithVehicleCapacity(orderTypeCallbackIndex, 0, // null capacity slack
					data.vehiclesServingOrderType.get(orderType), // vehicle maximum capacities
					true, // start cumul to zero
					orderType);
		}

		// Put restrictions for zone
		for (String zone : data.allZones) {
			int zoneCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
				int fromNode = manager.indexToNode(fromIndex);
				return data.zonePresentInOrders.get(zone)[fromNode];
			});
			routing.addDimensionWithVehicleCapacity(zoneCallbackIndex, 0, // null capacity slack
					data.vehiclesServingZone.get(zone), // vehicle maximum capacities
					true, // start cumul to zero
					zone);
		}

		// Add time window constraints for each location except depot.
		for (int i = data.vehicleCount; i < data.timeWindows.length; ++i) {
			long index = manager.nodeToIndex(i);
			try {
				timeDimension.cumulVar(index).setRange(data.timeWindows[i][0], data.timeWindows[i][1]);
				PickupOrder pickupOrder = data.pickupOrders.get(i);
				Integer serviceTimeForOrder = SERVICE_TIME_MAP.get(pickupOrder.orderType);
				int serviceTime = serviceTimeForOrder != null ? serviceTimeForOrder : SERVICE_TIME;
				timeDimension.slackVar(index).setRange(serviceTime, MAX_WAIT_TIME);
			} catch (Exception e) {
				System.out.println("Time window for order "+ i + " is not correct so dropping the order.");
			}
		}
		// Add time window constraints for each vehicle start node.
		for (int i = 0; i < data.vehicleCount; ++i) {
			long startIndex = routing.start(i);
			long endIndex = routing.end(i);
			try {
				timeDimension.cumulVar(startIndex).setRange(data.timeWindows[i][0], data.timeWindows[i][1]);
				timeDimension.cumulVar(endIndex).setRange(data.vehicles.get(i).startTimeMinutes, data.vehicles.get(i).endTimeMinutes);
				timeDimension.setCumulVarSoftLowerBound(endIndex, 660, 1000);
				//timeDimension.cumulVar(endIndex).removeInterval(0, 120);
			} catch (Exception e) {
				System.out.println("Time window for depot is not smallest");
			}
		}

		// Instantiate route start and end times to produce feasible times.
		for (int i = 0; i < data.vehicleCount; ++i) {
			routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.start(i)));
			routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.end(i)));
		}

		//Allow to drop nodes. Higher penalty on dropping the already confirmed orders.
		for (int i = data.vehicleCount; i < data.timeMatrix.length; ++i) {
			PickupOrder pickupOrder = data.pickupOrders.get( i - data.vehicleCount);
			long penalty;
			if (pickupOrder.prevStatus == 4) {
				penalty = Integer.MAX_VALUE;
			}
			else {
				penalty = Integer.MAX_VALUE / 2;
			}
			routing.addDisjunction(new long[] {manager.nodeToIndex(i)}, penalty);
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

	public Map<String, List<RouteLocation>> getVehiclesRoutes(
			VehicleRoutingInputData data, RoutingModel routing, RoutingIndexManager manager, Assignment solution) {
		// Solution cost.
		if(solution == null) {
			throw new RuntimeException("Route is not possible within given time slots");
		}

		// Inspect solution.
		RoutingDimension timeDimension = routing.getMutableDimension(TIME_DIMENSION);
		RoutingDimension distanceDimension = routing.getMutableDimension(DISTANCE_DIMENSION);
		long totalTime = 0;
		Map<String, List<RouteLocation>> allVehiclesRoute = new HashMap<>();
		for (int i = 0; i < data.vehicleCount; ++i) {
			List<RouteLocation> route = new ArrayList<>();
			long index = routing.start(i);
			IntVar startTimeVar = timeDimension.cumulVar(index);
			int startPointIndex = manager.indexToNode(index);
			if(startPointIndex != i) {
				throw new RuntimeException("start pickup index must be same as vehicle index");
			}
			RouteLocation startRouteLocation = new RouteLocation(startPointIndex, data.vehicles.get(i).id, solution.min(startTimeVar), solution.max(startTimeVar), data.vehicles.get(i).latitude, data.vehicles.get(i).longitude);
			startRouteLocation.distanceFromPrevNode = -1;
			startRouteLocation.distanceSoFar = (int) solution.min(distanceDimension.cumulVar(index));
			route.add(startRouteLocation);
			index = solution.value(routing.nextVar(index));
			Location prevLocation = null;
			Location currentLocation = data.vehicles.get(i);

			while (!routing.isEnd(index)) {
				IntVar timeVar = timeDimension.cumulVar(index);
				int vehiclesPlusOrderIndex = manager.indexToNode(index);
				prevLocation = currentLocation;
				currentLocation = data.pickupOrders.get(vehiclesPlusOrderIndex - data.vehicleCount);
				PickupOrder currentOrder = (PickupOrder) currentLocation;
				currentOrder.status = PickupOrderStatus.CONFIRMED;
				int distanceFromPrevLocation = (int) distance(prevLocation.latitude, prevLocation.longitude, currentLocation.latitude, currentLocation.longitude, "K");
				RouteLocation routeLocation = new RouteLocation(vehiclesPlusOrderIndex, currentLocation.id, solution.min(timeVar), solution.max(timeVar), currentLocation.latitude, currentLocation.longitude);
				routeLocation.distanceFromPrevNode = distanceFromPrevLocation;
				routeLocation.distanceSoFar = (int) solution.min(distanceDimension.cumulVar(index));
				route.add(routeLocation);
				index = solution.value(routing.nextVar(index));
			}
			IntVar endTimeVar = timeDimension.cumulVar(index);
			int endPointIndex = manager.indexToNode(index);
			if(endPointIndex != i) {
				throw new RuntimeException("end pickup index must be same as vehicle index");
			}
			RouteLocation endRouteLocation = new RouteLocation(endPointIndex, data.vehicles.get(i).id, solution.min(endTimeVar), solution.max(endTimeVar), data.vehicles.get(i).latitude, data.vehicles.get(i).longitude);
			int distance = 0;
			if(prevLocation != null) {
				distance = (int) distance(prevLocation.latitude, prevLocation.longitude, currentLocation.latitude, currentLocation.longitude, "K");
			}
			endRouteLocation.distanceFromPrevNode = distance;
			endRouteLocation.distanceSoFar = (int) solution.min(distanceDimension.cumulVar(index));
			route.add(endRouteLocation);

			totalTime += solution.min(endTimeVar);
			allVehiclesRoute.put(data.vehicles.get(i).id, route);
		}
		return allVehiclesRoute;
	}

	public void updateOrdersStatusInDB(Map<String, List<RouteLocation>> allVehiclesRoute, List<PickupOrder> droppedOrders, Map<String, Vehicle> vehiclesMap) {
		for (Map.Entry<String, List<RouteLocation>> vehicleRoute : allVehiclesRoute.entrySet()) {
			String vehicleId  = vehicleRoute.getKey();
			List<RouteLocation> routeLocations = vehicleRoute.getValue();
			Vehicle assignedVehicle = vehiclesMap.get(vehicleId);
			// Remove first and last nodes because that is vehicle place
			routeLocations.remove(routeLocations.size()-1);
			routeLocations.remove(0);
			//List<String> orderIds = routeLocations.stream().map(order -> order.orderId).collect(Collectors.toList());
			lgpOrderRepository.updateOrderStatusSuccess(routeLocations, assignedVehicle);
		}

		List<String> droppedOrderIds = droppedOrders.stream().map(order -> order.id).collect(Collectors.toList());
		lgpOrderRepository.updateOrderStatusFailure(droppedOrderIds);
	}

	private static long[][] computeTimeMatrix(List<PickupOrder> pickupOrderList, List<Vehicle> vehicleList) {
		int orderSize = pickupOrderList.size();
		int vehicleSize = vehicleList.size();
		int totalSize = orderSize+vehicleSize;
		List<Location> vehicleAndOrdersList = new ArrayList<>();
		vehicleAndOrdersList.addAll(vehicleList);
		vehicleAndOrdersList.addAll(pickupOrderList);
		long[][] timeMatrix = new long[totalSize][totalSize];

		for (int fromNode = 0; fromNode < totalSize; ++fromNode) {
			for (int toNode = 0; toNode < totalSize; ++toNode) {
				Location point1 = vehicleAndOrdersList.get(fromNode);
				Location point2 = vehicleAndOrdersList.get(toNode);
				long distanceInKm = distance(point1.latitude, point1.longitude, point2.latitude, point2.longitude, "K");
				//long approaxDistance = (long) Math.ceil(distanceInKm);
				long approaximateTimeInMinutes = distanceInKm * 60 / VEHICLE_SPEED;
				timeMatrix[fromNode][toNode] = approaximateTimeInMinutes;
			}
		}

		//printMatrix(timeMatrix);
		return timeMatrix;
	}

	private static long[][] computeDistanceMatrix(List<PickupOrder> pickupOrderList, List<Vehicle> vehicleList) {
		int orderSize = pickupOrderList.size();
		int vehicleSize = vehicleList.size();
		int totalSize = orderSize+vehicleSize;
		List<Location> vehicleAndOrdersList = new ArrayList<>();
		vehicleAndOrdersList.addAll(vehicleList);
		vehicleAndOrdersList.addAll(pickupOrderList);
		long[][] distanceMatrix = new long[totalSize][totalSize];

		for (int fromNode = 0; fromNode < totalSize; ++fromNode) {
			for (int toNode = 0; toNode < totalSize; ++toNode) {
				Location point1 = vehicleAndOrdersList.get(fromNode);
				Location point2 = vehicleAndOrdersList.get(toNode);
				double distanceInKm = distance(point1.latitude, point1.longitude, point2.latitude, point2.longitude, "K");
				long approaxDistance = Math.round(distanceInKm);
				distanceMatrix[fromNode][toNode] = approaxDistance;
			}
		}

		//printMatrix(distanceMatrix);
		return distanceMatrix;
	}

	private static long[][] getTimeSlots(List<PickupOrder> pickupOrderList, List<Vehicle> vehicleList) {
		int orderSize = pickupOrderList.size();
		int vehicleSize = vehicleList.size();
		int totalSize = orderSize+vehicleSize;
		long[][] timeSlots = new long[totalSize][2];
		for (int i = 0; i < vehicleSize; ++i) {
			// For vehicles start time and end time will be same in time slot array.
			timeSlots[i][0] = vehicleList.get(i).startTimeMinutes;
			timeSlots[i][1] = vehicleList.get(i).startTimeMinutes;
		}
		for (int i = 0; i < orderSize; ++i) {
			timeSlots[i + vehicleSize][0] = pickupOrderList.get(i).startTimeMinutes - BUFFER_MINUTES;
			timeSlots[i + vehicleSize][1] = pickupOrderList.get(i).endTimeMinutes + BUFFER_MINUTES;
		}
		//printMatrix(timeSlots);
		return timeSlots;
	}

	public static void printMatrix(long[][] matrix) {
		for (int i = 0; i < matrix.length; i++) { //this equals to the row in our matrix.
			for (int j = 0; j < matrix[i].length; j++) { //this equals to the column in each row.
				System.out.print(matrix[i][j] + " ");
			}
			System.out.println(); //change line on console as row comes to end in the matrix.
		}
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
			long distance = (long) Math.ceil(dist);
			return distance;
		}
	}
}

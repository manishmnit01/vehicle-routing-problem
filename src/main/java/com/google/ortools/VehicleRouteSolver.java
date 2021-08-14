package com.google.ortools;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VehicleRouteSolver {

	private static final int SERVICE_TIME = 10;
	private static final int VEHICLE_SPEED = 20;

	public static void main(String[] args) throws Exception {
		Loader.loadNativeLibraries();

		final VehicleRoutingInputData data = createTestData();
		RoutingIndexManager manager = new RoutingIndexManager(data.timeMatrix.length, data.vehicleNumber, data.starts, data.ends);

		// Create Routing Model.
		RoutingModel routing = new RoutingModel(manager);

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
					return data.timeMatrix[fromNode][toNode] + SERVICE_TIME;
				}
			});

		// Define cost of each arc.
		routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

		// Add Time constraint.
		routing.addDimension(transitCallbackIndex, // transit callback
				12 * 60, // Maximum waiting time one vehicle can wait from one order to next
				24 * 60, // Time before this vehicle has to reach the end depot.
				false, // start cumul to zero
				"Time");
		RoutingDimension timeDimension = routing.getMutableDimension("Time");
		timeDimension.setGlobalSpanCostCoefficient(100);

		// Add time window constraints for each location except depot.
		for (int i = data.vehicleNumber; i < data.timeWindows.length; ++i) {
			long index = manager.nodeToIndex(i);
			try {
				timeDimension.cumulVar(index).setRange(data.timeWindows[i][0], data.timeWindows[i][1]);
			} catch (Exception e) {
				System.out.println("Time window for order "+ i + " is not correct so dropping the order.");
			}
		}
		// Add time window constraints for each vehicle start node.
		for (int i = 0; i < data.vehicleNumber; ++i) {
			long startIndex = routing.start(i);
			long endIndex = routing.end(i);
			try {
				timeDimension.cumulVar(startIndex).setRange(data.timeWindows[i][0], data.timeWindows[i][1]);
				timeDimension.cumulVar(endIndex).setRange(data.vehicles.get(i).startTimeMinutes, data.vehicles.get(i).endTimeMinutes);
			} catch (Exception e) {
				System.out.println("Time window for depot is not smallest");
			}
		}

		// Instantiate route start and end times to produce feasible times.
		for (int i = 0; i < data.vehicleNumber; ++i) {
			routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.start(i)));
			routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.end(i)));
		}

		//Allow to drop nodes.
		long penalty = Integer.MAX_VALUE;
		for (int i = 1; i < data.timeMatrix.length; ++i) {
			routing.addDisjunction(new long[] {manager.nodeToIndex(i)}, penalty);
		}

		RoutingSearchParameters searchParameters =
				main.defaultRoutingSearchParameters()
						.toBuilder()
						.setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
						.setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
						.setTimeLimit(Duration.newBuilder().setSeconds(5).build())
						.build();

		Assignment solution = routing.solveWithParameters(searchParameters);
		printSolution(data, routing, manager, solution);
	}

	public static VehicleRoutingInputData createTestData() {

		String line = "";
		String splitBy = ",";
		VehicleRoutingInputData data = new VehicleRoutingInputData();

		List<PickupOrder> pickupOrderList = data.pickupOrders;
		List<Vehicle> vehicleList = data.vehicles;
		try
		{
			BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\manisha\\Downloads\\orders.csv"));
			boolean orderFlag = true;
			while ((line = br.readLine()) != null)   //returns a Boolean value
			{
				String[] orders = line.split(splitBy);
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
					pickupOrderList.add(new PickupOrder(id, latitute, longitude, startTimeMinutes, endTimeMinutes));
				}
				else {
					vehicleList.add(new Vehicle(id, latitute, longitude, startTimeMinutes, endTimeMinutes));
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		data.timeMatrix = computeTimeMatrix(pickupOrderList, vehicleList);
		data.timeWindows = getTimeSlots(pickupOrderList, vehicleList);
		data.vehicleNumber = 2;
		data.starts = new int[] {0, 1};
		data.ends = new int[] {0, 1};
		data.depot = 0;
		return data;
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
				double distanceInKm = distance(point1.latitude, point1.longitude, point1.latitude, point2.longitude, "K");
				long approaxDistance = Math.round(distanceInKm);
				long approaximateTimeInMinutes = approaxDistance * 60 / VEHICLE_SPEED;
				timeMatrix[fromNode][toNode] = approaximateTimeInMinutes;
			}
		}

		printMatrix(timeMatrix);
		return timeMatrix;
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
			timeSlots[i + vehicleSize][0] = pickupOrderList.get(i).startTimeMinutes;
			timeSlots[i + vehicleSize][1] = pickupOrderList.get(i).endTimeMinutes;
		}
		printMatrix(timeSlots);
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

	private static double distance(double lat1, double lon1, double lat2, double lon2, String unit) {
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
			return (dist);
		}
	}

	public static void printSolution(
			VehicleRoutingInputData data, RoutingModel routing, RoutingIndexManager manager, Assignment solution) {
		// Solution cost.
		if(solution == null) {
			throw new RuntimeException("Route is not possible within given time slots");
		}

		// Inspect solution.
		RoutingDimension timeDimension = routing.getMutableDimension("Time");
		long totalTime = 0;
		Map<String, List<RouteNode>> allVehiclesRoute = new HashMap<>();
		for (int i = 0; i < data.vehicleNumber; ++i) {
			List<RouteNode> route = new ArrayList<>();
			long index = routing.start(i);
			IntVar startTimeVar = timeDimension.cumulVar(index);
			int startPointIndex = manager.indexToNode(index);
			if(startPointIndex != i) {
				throw new RuntimeException("start pickup index must be same as vehicle index");
			}
			RouteNode startRouteNode = new RouteNode(startPointIndex, data.vehicles.get(i).id, solution.min(startTimeVar), solution.max(startTimeVar), data.vehicles.get(i).latitude, data.vehicles.get(i).longitude, -1);
			route.add(startRouteNode);
			index = solution.value(routing.nextVar(index));
			Location prevLocation = null;
			Location currentLocation = data.vehicles.get(i);

			while (!routing.isEnd(index)) {
				IntVar timeVar = timeDimension.cumulVar(index);
				int vehiclesPlusOrderIndex = manager.indexToNode(index);
				prevLocation = currentLocation;
				currentLocation = data.pickupOrders.get(vehiclesPlusOrderIndex - data.vehicleNumber);
				PickupOrder currentOrder = (PickupOrder) currentLocation;
				currentOrder.status = PickupOrderStatus.CONFIRMED;
				double distanceFromPrevLocation = distance(prevLocation.latitude, prevLocation.longitude, currentLocation.latitude, currentLocation.longitude, "K");
				RouteNode routeNode = new RouteNode(vehiclesPlusOrderIndex, currentLocation.id, solution.min(timeVar), solution.max(timeVar), currentLocation.latitude, currentLocation.longitude, distanceFromPrevLocation);
				route.add(routeNode);
				index = solution.value(routing.nextVar(index));
			}
			IntVar endTimeVar = timeDimension.cumulVar(index);
			int endPointIndex = manager.indexToNode(index);
			if(endPointIndex != i) {
				throw new RuntimeException("end pickup index must be same as vehicle index");
			}
			RouteNode endRouteNode = new RouteNode(endPointIndex, data.vehicles.get(i).id, solution.min(startTimeVar), solution.max(startTimeVar), data.vehicles.get(i).latitude, data.vehicles.get(i).longitude, -1);
			route.add(endRouteNode);

			totalTime += solution.min(endTimeVar);
			allVehiclesRoute.put(data.vehicles.get(i).id, route);
		}
		long totaHour = totalTime / 60;
		long totalMinutes = totalTime % 60;
		System.out.println("Total time is " + totaHour + ":" + totalMinutes);
		for (String key : allVehiclesRoute.keySet()) {
			String value = allVehiclesRoute.get(key).toString();
			System.out.println(key + " => " + value);
		}

		List<PickupOrder> droppedOrders = data.pickupOrders.stream().filter(order -> order.status == PickupOrderStatus.PENDING).collect(Collectors.toList());
		System.out.println("Following orders were dropped:-");
		for (PickupOrder droppedOrder : droppedOrders) {
			System.out.println(droppedOrder.id);
		}
	}
}

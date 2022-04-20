package com.tg.vehicleroutingv2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/v2/vrp")
public class VehicleRoutingController {

	@Autowired
	private VehicleRoutingService vehicleRoutingService;

	@Autowired
	private VehicleRoutingRepository vehicleRoutingRepository;

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public VehicleRoutingResponse uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("assignmentId") String assignmentId,
											 @RequestParam(value = "buffer", required = false, defaultValue = "0") int buffer,
											 @RequestParam(value = "maxDistance", required = false, defaultValue = "50") int maxDistance,
											 @RequestParam(value = "useDistanceApi", required = false, defaultValue = "false") boolean useDistanceApi) throws Exception {

		VehicleRoutingResponse response = new VehicleRoutingResponse();
		OrderAssignmentData orderAssignment = vehicleRoutingService.verifyOrderAssignment(assignmentId);
		if (orderAssignment == null) {
			response.status = "Failure";
			response.statusCode = 400;
			response.message = "Invalid assignmentId";
			return response;
		}

		Map<String, VehicleRoutingData> zoneToVehicleRoutingDataMap;
		try {
			zoneToVehicleRoutingDataMap = vehicleRoutingService.createVehicleRouteData(file, buffer, useDistanceApi);
		} catch (Exception e) {
			response.status = "Failure";
			response.statusCode = 500;
			response.message = e.getMessage();
			e.printStackTrace();
			return response;
		}

		maxDistance = maxDistance * 1000; // convert km to meters
		response.data = vehicleRoutingService.generateRoutesForAllZones(zoneToVehicleRoutingDataMap, assignmentId, maxDistance);
		return response;
	}

	@RequestMapping(value = "/assignNewOrder", method = RequestMethod.POST)
	public NewOrderAssignResponse assignNewOrder(@RequestParam("orderId") String orderId,
											@RequestParam("zoneId") String zoneId,
											 @RequestParam("latitude") double latitude,
											 @RequestParam(value = "longitude") double longitude,
											 @RequestParam(value = "startTime") String startTime,
											 @RequestParam(value = "endTime") String endTime,
											 @RequestParam(value = "date") int date,
											 @RequestParam(value = "month") int month,
											 @RequestParam(value = "year") int year,
						   					 @RequestParam(value = "useDistanceApi", required = false, defaultValue = "false") boolean useDistanceApi
												) throws Exception {

		NewOrderAssignResponse response = new NewOrderAssignResponse();
		List<LgpOrder> assignedOrders = vehicleRoutingRepository.getAssignedOrdersOfZone(zoneId, date, month, year);
		Map<Integer, List<PickupNode>>  routeIdPickupOrdersMap = new HashMap<>();
		for (LgpOrder assignedOrder : assignedOrders) {
			PickupNode pickupNode = new PickupNode(assignedOrder);
			routeIdPickupOrdersMap.putIfAbsent(assignedOrder.routeId, new ArrayList<>());
			routeIdPickupOrdersMap.get(assignedOrder.routeId).add(pickupNode);
		}

		String[] startTimeSplit = startTime.split(" ");
		int startHour = Integer.parseInt(startTimeSplit[0]);
		int startMinute = Integer.parseInt(startTimeSplit[1]);
		int startTimeMinutes = startHour * 60 + startMinute;

		String[] endTimeSplit = endTime.split(" ");
		int endHour = Integer.parseInt(endTimeSplit[0]);
		int endMinute = Integer.parseInt(endTimeSplit[1]);
		int endTimeMinutes = endHour * 60 + endMinute;

		PickupNode newOrder = new PickupNode(orderId, latitude, longitude, startTimeMinutes, endTimeMinutes);
		List<RouteNode> existingRouteForNewOrder = null;
		int routeIdForNewOrder = 0;
		for (Map.Entry<Integer,List<PickupNode>> entry : routeIdPickupOrdersMap.entrySet()) {
			int routeId = entry.getKey();
			List<PickupNode> sameRouteOrders = entry.getValue();
			sameRouteOrders.add(newOrder);
			VehicleRoutingData data = vehicleRoutingService.convertPickupOrdersToRoutingData(sameRouteOrders, 1, 0, useDistanceApi);
			try {
				existingRouteForNewOrder = vehicleRoutingService.createOneRouteForOrders(data, 50*1000);
				routeIdForNewOrder = routeId;
				break;
			} catch (Exception e) {

			}
		}

		if(existingRouteForNewOrder != null && routeIdForNewOrder > 0) {
			vehicleRoutingRepository.updateOrdersOfRoute(existingRouteForNewOrder, routeIdForNewOrder, zoneId);
			response.data = existingRouteForNewOrder;
		} else {
			response.message = "Not able to assign the order in existing routes";
			response.status = "Failure";
			response.statusCode = 400;
		}
		return response;
	}
}


package com.tg.routing;

import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/vrp")
public class VehicleRoutingController {

	@Autowired
	private VehicleRoutingService vehicleRoutingService;

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public RoutePlanResponse uploadFile(@RequestParam("file") MultipartFile file) {

		VehicleRoutingInputData data = vehicleRoutingService.createVehicleRouteData(file);
		RoutingIndexManager manager = new RoutingIndexManager(data.timeMatrix.length, data.vehicleNumber, data.starts, data.ends);
		RoutingModel routing = new RoutingModel(manager);
		Assignment solution = vehicleRoutingService.createRouteSolution(data, manager, routing);
		RoutePlanResponse routePlanResponse = new RoutePlanResponse();
		routePlanResponse.allVehiclesRoute = vehicleRoutingService.getVehiclesRoutes(data, routing, manager, solution);
		routePlanResponse.droppedOrders = data.pickupOrders.stream().filter(order -> order.status == PickupOrderStatus.PENDING).collect(Collectors.toList());
		vehicleRoutingService.updateOrdersStatusInDB(routePlanResponse.allVehiclesRoute, routePlanResponse.droppedOrders, data.vehiclesMap);
		return routePlanResponse;
	}
}

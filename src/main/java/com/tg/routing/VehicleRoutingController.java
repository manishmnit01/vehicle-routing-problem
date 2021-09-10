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

	@Autowired
	private OrderAssignmentService orderAssignmentService;

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public OrderAssignmentResponse uploadFile(@RequestParam("file") MultipartFile file) throws Exception {

		VehicleRoutingInputData vehicleRoutingInputData = vehicleRoutingService.createVehicleRouteData(file);
		String assignmentId = orderAssignmentService.createOrderAssignment(vehicleRoutingInputData.pickupOrders.size());
		OrderAssignmentResponse orderAssignmentResponse = new OrderAssignmentResponse();
		orderAssignmentResponse.assignmentId = assignmentId;

		orderAssignmentService.startAssignment(vehicleRoutingInputData, assignmentId);
		return orderAssignmentResponse;
	}
}

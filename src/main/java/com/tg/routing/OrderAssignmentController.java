package com.tg.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/vrp")
public class OrderAssignmentController {

	@Autowired
	private VehicleRoutingSolver vehicleRoutingSolver;

	@Autowired
	private OrderAssignmentService orderAssignmentService;

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public OrderAssignmentResponse uploadFile(@RequestParam("file") MultipartFile file) throws Exception {

		VehicleRoutingInputData vehicleRoutingInputData = vehicleRoutingSolver.createVehicleRouteData(file);
		String assignmentId = orderAssignmentService.createOrderAssignment(vehicleRoutingInputData.pickupOrders.size());
		OrderAssignmentResponse orderAssignmentResponse = new OrderAssignmentResponse();
		orderAssignmentResponse.assignmentId = assignmentId;

		orderAssignmentService.startAssignment(vehicleRoutingInputData, assignmentId);
		return orderAssignmentResponse;
	}
}

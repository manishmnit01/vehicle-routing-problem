package com.tg.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
	public OrderAssignmentResponse uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("assignmentId") String assignmentId) throws Exception {

		OrderAssignmentResponse orderAssignmentResponse = new OrderAssignmentResponse();
		if(!StringUtils.hasText(assignmentId)) {
			orderAssignmentResponse.status = "Failure";
			orderAssignmentResponse.statusCode = 400;
			orderAssignmentResponse.message = "Invalid assignmentId";
			return orderAssignmentResponse;
		}

		VehicleRoutingInputData vehicleRoutingInputData;
		try {
			OrderAssignment orderAssignment = orderAssignmentService.verifyOrderAssignment(assignmentId);
			if (orderAssignment == null) {
				orderAssignmentResponse.status = "Failure";
				orderAssignmentResponse.statusCode = 400;
				orderAssignmentResponse.message = "Invalid assignmentId";
				return orderAssignmentResponse;
			}
			vehicleRoutingInputData = vehicleRoutingSolver.createVehicleRouteData(file);
		} catch (Exception e) {
			orderAssignmentResponse.status = "Failure";
			orderAssignmentResponse.statusCode = 500;
			orderAssignmentResponse.message = e.getMessage();
			e.printStackTrace();
			return orderAssignmentResponse;
		}

		OrderAssignment orderAssignment = orderAssignmentService.startAssignment(vehicleRoutingInputData, assignmentId);
		return orderAssignmentResponse;
	}
}

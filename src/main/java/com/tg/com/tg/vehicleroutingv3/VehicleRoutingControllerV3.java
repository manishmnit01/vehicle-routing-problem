package com.tg.com.tg.vehicleroutingv3;

import com.tg.com.tg.vehicleroutingv3.VehicleRoutingServiceV3;
import com.tg.vehicleroutingv2.OrderAssignmentData;
import com.tg.vehicleroutingv2.VehicleRoutingData;
import com.tg.vehicleroutingv2.VehicleRoutingRepository;
import com.tg.vehicleroutingv2.VehicleRoutingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@RestController
@RequestMapping("/api/v3/vrp")
public class VehicleRoutingControllerV3 {

	@Autowired
	private VehicleRoutingServiceV3 vehicleRoutingService;

	@Autowired
	private VehicleRoutingRepository vehicleRoutingRepository;

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public VehicleRoutingResponse uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("assignmentId") String assignmentId,
											 @RequestParam(value = "buffer", required = false, defaultValue = "0") int buffer,
											 @RequestParam(value = "vehicleCount", required = false, defaultValue = "100") int vehicleCount) throws Exception {

		VehicleRoutingResponse response = new VehicleRoutingResponse();
		OrderAssignmentData orderAssignment = vehicleRoutingService.verifyOrderAssignment(assignmentId);
		if (orderAssignment == null) {
			response.status = "Failure";
			response.statusCode = 400;
			response.message = "Invalid assignmentId";
			return response;
		}

		VehicleRoutingData vehicleRoutingData;
		try {
			vehicleRoutingData = vehicleRoutingService.createVehicleRouteData(file, buffer, vehicleCount);
		} catch (Exception e) {
			response.status = "Failure";
			response.statusCode = 500;
			response.message = e.getMessage();
			return response;
		}

		response.data = vehicleRoutingService.generateRoutesForCity(vehicleRoutingData, assignmentId);
		return response;
	}
}



package com.tg.vehiclerouting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
	public List<AllRoutesForZone> uploadFile(@RequestParam("file") MultipartFile file) throws Exception {

		Map<String, VehicleRoutingData> zoneToVehicleRoutingDataMap = vehicleRoutingService.createVehicleRouteData(file);
		List<AllRoutesForZone> response = new ArrayList<>();
		for(Map.Entry<String, VehicleRoutingData> entry : zoneToVehicleRoutingDataMap.entrySet()) {
			String zone = entry.getKey();
			VehicleRoutingData vehicleRoutingData = entry.getValue();
			AllRoutesForZone allRoutesForZone = vehicleRoutingService.createAllRoutesForZone(vehicleRoutingData, zone);
			vehicleRoutingRepository.updateAllOrdersForZone(allRoutesForZone);
			response.add(allRoutesForZone);
		}

		return response;
	}
}


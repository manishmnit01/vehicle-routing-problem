package com.google.ortools;

import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.sun.jna.platform.WindowUtils;
import org.scijava.nativelib.NativeLibraryUtil;
import org.scijava.nativelib.NativeLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/vrp")
public class VehicleRoutingController {

	@Autowired
	private VehicleRoutingService vehicleRoutingService;

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public RoutePlanResponse uploadFile(@RequestParam("file") MultipartFile file) {

		//Loader.loadNativeLibraries();
		//NativeUtils.loadLibraryFromJar("/jniortools.dll");
		//System.load("C:\\Users\\manisha\\work2\\vehicle-routing-problem\\src\\main\\resources\\win32-x86-64\\jniortools.dll");
		try {
			//
			NativeLoader.loadLibrary("jniortools");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		VehicleRoutingInputData data = vehicleRoutingService.createVehicleRouteData(file);
		RoutingIndexManager manager = new RoutingIndexManager(data.timeMatrix.length, data.vehicleNumber, data.starts, data.ends);

		// Create Routing Model.
		RoutingModel routing = new RoutingModel(manager);
		Assignment solution = vehicleRoutingService.createRouteSolution(data, manager, routing);
		RoutePlanResponse routePlanResponse = new RoutePlanResponse();
		routePlanResponse.allVehiclesRoute = vehicleRoutingService.getVehiclesRoutes(data, routing, manager, solution);
		routePlanResponse.droppedOrders = data.pickupOrders.stream().filter(order -> order.status == PickupOrderStatus.PENDING).collect(Collectors.toList());
		return routePlanResponse;
	}
}

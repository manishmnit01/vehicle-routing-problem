package com.tg.routing;

import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderAssignmentService {

	@Autowired
	private OrderAssignmentRepository orderAssignmentRepository;

	@Autowired
	private VehicleRoutingService vehicleRoutingService;

	public String createOrderAssignment(int total) {
		OrderAssignment orderAssignment = new OrderAssignment();
		String assignmentId = UUID.randomUUID().toString();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = OrderAssignmentStatus.PROCESSING;
		orderAssignment.total = total;
		orderAssignmentRepository.insert(orderAssignment);
		return assignmentId;
	}

	@Async
	public RoutePlanResponse startAssignment(VehicleRoutingInputData data, String assignmentId) {

		OrderAssignmentStatus orderAssignmentStatus = OrderAssignmentStatus.COMPLETED;
		String failedMessage = null;
		int dropped = 0;
		int assigned = 0;
		RoutePlanResponse routePlanResponse = new RoutePlanResponse();
		try {
			RoutingIndexManager manager = new RoutingIndexManager(data.timeMatrix.length, data.vehicleCount, data.starts, data.ends);
			RoutingModel routing = new RoutingModel(manager);
			Assignment solution = vehicleRoutingService.createRouteSolution(data, manager, routing);
			routePlanResponse.allVehiclesRoute = vehicleRoutingService.getVehiclesRoutes(data, routing, manager, solution);
			routePlanResponse.droppedOrders = data.pickupOrders.stream().filter(order -> order.status == PickupOrderStatus.PENDING).collect(Collectors.toList());
			vehicleRoutingService.updateOrdersStatusInDB(routePlanResponse.allVehiclesRoute, routePlanResponse.droppedOrders, data.vehiclesMap);
			dropped = routePlanResponse.droppedOrders.size();
			assigned = data.pickupOrders.size() - dropped;
		} catch (Exception e)  {
			orderAssignmentStatus = OrderAssignmentStatus.FAILED;
			e.printStackTrace();
			failedMessage = e.getMessage();
		}

		orderAssignmentRepository.update(assignmentId, orderAssignmentStatus, assigned, dropped, failedMessage);
		return routePlanResponse;
	}
}

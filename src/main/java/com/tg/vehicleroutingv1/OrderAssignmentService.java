package com.tg.vehicleroutingv1;

import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderAssignmentService {

	@Autowired
	private OrderAssignmentRepository orderAssignmentRepository;

	@Autowired
	private VehicleRoutingSolver vehicleRoutingSolver;

	public String createOrderAssignment(int total) {
		OrderAssignment orderAssignment = new OrderAssignment();
		String assignmentId = UUID.randomUUID().toString();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = OrderAssignmentStatus.PROCESSING;
		orderAssignment.total = total;
		orderAssignmentRepository.insert(orderAssignment);
		return assignmentId;
	}

	public OrderAssignment verifyOrderAssignment(String assignmentId) {
		//String assignmentId = UUID.randomUUID().toString();
		OrderAssignment orderAssignment = new OrderAssignment();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = OrderAssignmentStatus.PROCESSING;
		return orderAssignmentRepository.update(orderAssignment);
	}

	public OrderAssignment startAssignment(VehicleRoutingInputData data, String assignmentId) {

		OrderAssignment orderAssignment = new OrderAssignment();
		orderAssignment.assignmentId = assignmentId;
		orderAssignment.status = OrderAssignmentStatus.COMPLETED;
		orderAssignment.failedMessage = null;
		orderAssignment.dropped = 0;
		orderAssignment.assigned = 0;
		orderAssignment.total = data.pickupOrders.size();
		RoutePlanResponse routePlanResponse = new RoutePlanResponse();
		try {
			RoutingIndexManager manager = new RoutingIndexManager(data.timeMatrix.length, data.vehicleCount, data.starts, data.ends);
			RoutingModel routing = new RoutingModel(manager);
			Assignment solution = vehicleRoutingSolver.createRouteSolution(data, manager, routing);
			routePlanResponse.allVehiclesRoute = vehicleRoutingSolver.getVehiclesRoutes(data, routing, manager, solution);
			routePlanResponse.droppedOrders = data.pickupOrders.stream().filter(order -> order.status == PickupOrderStatus.PENDING).collect(Collectors.toList());
			vehicleRoutingSolver.updateOrdersStatusInDB(routePlanResponse.allVehiclesRoute, routePlanResponse.droppedOrders, data.vehiclesMap);
			orderAssignment.dropped = routePlanResponse.droppedOrders.size();
			orderAssignment.assigned = orderAssignment.total - orderAssignment.dropped;
		} catch (Exception e)  {
			orderAssignment.status = OrderAssignmentStatus.FAILED;
			orderAssignment.failedMessage = e.getMessage();
			e.printStackTrace();
		}

		OrderAssignment orderAssignmentUpdated = orderAssignmentRepository.update(orderAssignment);
		orderAssignmentUpdated.routePlan = routePlanResponse;
		return orderAssignmentUpdated;
	}
}

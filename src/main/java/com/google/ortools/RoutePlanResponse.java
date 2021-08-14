package com.google.ortools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoutePlanResponse {

	public Map<String, List<RouteNode>> allVehiclesRoute = new HashMap<>();

	public List<PickupOrder> droppedOrders = new ArrayList<>();
}

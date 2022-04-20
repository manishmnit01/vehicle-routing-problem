package com.tg.vehicleroutingv2;

import java.util.*;

/** VRPTW. */
public class VehicleRoutingData {

	public long[][] timeMatrix;

	public long[][] distanceMatrix;

	public long[][] timeWindows;

	public List<PickupNode> pickupNodes = new ArrayList<>();

	public int vehicleCount;

	public int orderCount;

	public int depot;

	public Set<String> allZones = new HashSet<>();

	public Map<String, long[]> zonePresentInOrders = new HashMap<>();

}

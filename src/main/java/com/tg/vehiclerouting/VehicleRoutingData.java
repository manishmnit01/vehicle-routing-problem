package com.tg.vehiclerouting;

import java.util.*;

/** VRPTW. */
public class VehicleRoutingData {

	public long[][] timeMatrix;

	public long[][] distanceMatrix;

	public long[][] timeWindows;

	List<PickupNode> pickupNodes = new ArrayList<>();

	public int vehicleCount;

	public int orderCount;

	public int depot;
}

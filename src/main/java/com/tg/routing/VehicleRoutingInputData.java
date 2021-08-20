package com.tg.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** VRPTW. */
public class VehicleRoutingInputData {

  public long[][] timeMatrix;

  public long[][] timeWindows;

  List<PickupOrder> pickupOrders = new ArrayList<>();

  List<Vehicle> vehicles = new ArrayList<>();

  Map<String, Vehicle> vehiclesMap = new HashMap<>();

  public int vehicleNumber;

  public int depot;

  public int[] starts;

  public int[] ends;
}


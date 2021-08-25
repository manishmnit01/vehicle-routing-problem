package com.tg.routing;

import java.util.*;

/** VRPTW. */
public class VehicleRoutingInputData {

  public long[][] timeMatrix;

  public long[][] timeWindows;

  List<PickupOrder> pickupOrders = new ArrayList<>();

  List<Vehicle> vehicles = new ArrayList<>();

  Map<String, Vehicle> vehiclesMap = new HashMap<>();

  Set<String> allZones = new HashSet<>();

  Set<String> allOrderTypes = new HashSet<>();

  Map<String, long[]> orderTypePresentInOrders = new HashMap<>();

  Map<String, long[]> vehiclesServingOrderType = new HashMap<>();

  Map<String, long[]> zonePresentInOrders = new HashMap<>();

  Map<String, long[]> vehiclesServingZone = new HashMap<>();

  public int vehicleCount;

  public int orderCount;

  public int depot;

  public int[] starts;

  public int[] ends;
}


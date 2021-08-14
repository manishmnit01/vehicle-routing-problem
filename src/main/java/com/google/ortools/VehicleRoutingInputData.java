package com.google.ortools;

import java.util.ArrayList;
import java.util.List;

/** VRPTW. */
public class VehicleRoutingInputData {

  public long[][] timeMatrix;

  public long[][] timeWindows;

  List<PickupOrder> pickupOrders = new ArrayList<>();

  List<Vehicle> vehicles = new ArrayList<>();

  public int vehicleNumber;

  public int depot;

  public int[] starts;

  public int[] ends;
}


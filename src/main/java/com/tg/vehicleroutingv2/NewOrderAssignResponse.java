package com.tg.vehicleroutingv2;

import java.util.List;

public class NewOrderAssignResponse {

	public List<RouteNode> data;

	public String status = "Success";

	public int statusCode = 200;

	public String message = "Order Assigned";
}

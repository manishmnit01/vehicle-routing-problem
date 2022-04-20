package com.tg.vehicleroutingv2;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document
public class OrderAssignmentData {

	@Id
	public String assignmentId;

	public String status;

	public String failedMessage;

	public int totalRoutes;

	public List<ZoneWiseRoutes> allRoutesForCity;

	public List<List<RouteNode>> allRoutes;

	public Date updatedTime = new Date();
}

package com.tg.vehicleroutingv2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Repository
public class VehicleRoutingRepository {

	@Autowired
	private MongoTemplate mongoTemplate;

	public void updateAllOrdersForZone(ZoneWiseRoutes zoneWiseRoutes, int routeId) {
		for(List<RouteNode> route : zoneWiseRoutes.allRoutesForZone) {
			this.updateOrdersOfRoute(route, routeId, zoneWiseRoutes.zone);
			routeId++;
		}
	}

	public void updateOrdersOfRoute(List<RouteNode> route, int routeId, String zoneId) {
		int pickupSeq = 10;
		for (RouteNode routeNode : route) {
			Criteria criteria = Criteria.where("orderId").is(routeNode.orderId);
			Query query = new Query(criteria);
			Update update = new Update();
			update.set("zone_id", zoneId);
			update.set("astatus", 100);
			update.set("pickupSeq", pickupSeq);
			update.set("routeId", routeId);
			update.set("distanceFromPrevNode", routeNode.distanceFromPrevNode);
			update.set("distanceSoFar", routeNode.distanceSoFar);
			update.set("reachTime", routeNode.reachTime);
			update.set("is_beyond_slot_time", routeNode.isBeyondSlotTime);
			update.set("startTimeMinutes", (int) routeNode.startTimeMinutes);
			update.set("endTimeMinutes", (int) routeNode.endTimeMinutes);
			mongoTemplate.findAndModify(query, update, Map.class, "LgpOrder");
			pickupSeq = pickupSeq + 10;
		}
	}

	public void updateAllOrdersForCity(List<List<RouteNode>> allRoutes) {
		int routeId = 1;
		for(List<RouteNode> route : allRoutes) {
			int pickupSeq = 10;
			for (RouteNode routeNode : route) {
				Criteria criteria = Criteria.where("orderId").is(routeNode.orderId);
				Query query = new Query(criteria);
				Update update = new Update();
				update.set("astatus", 100);
				update.set("pickupSeq", pickupSeq);
				update.set("routeId", routeId);
				update.set("distanceFromPrevNode", routeNode.distanceFromPrevNode);
				update.set("distanceSoFar", routeNode.distanceSoFar);
				update.set("reachTime", routeNode.reachTime);
				update.set("is_beyond_slot_time", routeNode.isBeyondSlotTime);
				mongoTemplate.findAndModify(query, update, Map.class, "LgpOrder");
				pickupSeq = pickupSeq + 10;
			}
			routeId++;
		}
	}

	public OrderAssignmentData updateOrderAssignment(OrderAssignmentData orderAssignment) {

		Criteria criteria = Criteria.where("_id").is(orderAssignment.assignmentId);
		Query query = new Query(criteria);
		Update update = new Update();
		update.set("status", orderAssignment.status);
		update.set("totalRoutes", orderAssignment.totalRoutes);
		update.set("failedMessage", orderAssignment.failedMessage);
		update.set("updatedTime", new Date());
		return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), OrderAssignmentData.class,"OrderAssignments");
	}

	public List<LgpOrder> getAssignedOrdersOfZone(String zoneId, int date, int month, int year) {

		Criteria criteria = Criteria.where("zone_id").is(zoneId).and("date").is(date).and("month").is(month).and("year").is(year);
		Query query = new Query(criteria);
		List<LgpOrder> assignedOrders = mongoTemplate.find(query, LgpOrder.class);
		return assignedOrders;
	}
}

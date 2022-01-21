package com.tg.vehiclerouting;

import com.tg.routing.RouteLocation;
import com.tg.routing.Vehicle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class VehicleRoutingRepository {

	@Autowired
	private MongoTemplate mongoTemplate;

	public void updateAllOrdersForZone(AllRoutesForZone allRoutesForZone) {
		int routeId = 1;
		for(List<RouteNode> route : allRoutesForZone.allRoutes) {
			int pickupSeq = 10;
			for (RouteNode routeNode : route) {
				Criteria criteria = Criteria.where("orderId").is(routeNode.orderId);
				Query query = new Query(criteria);
				Update update = new Update();
				update.set("orderStatus", 100);
				update.set("pickupSeq", pickupSeq);
				update.set("routeId", routeId);
				update.set("distanceFromPrevNode", routeNode.distanceFromPrevNode);
				update.set("distanceSoFar", routeNode.distanceSoFar);
				update.set("reachTime", routeNode.reachTime);
				mongoTemplate.findAndModify(query, update, Map.class, "LgpOrder");
				pickupSeq = pickupSeq + 10;
			}
			routeId++;
		}
	}
}

package com.tg.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class LgpOrderRepository {

	@Autowired
	private MongoTemplate mongoTemplate;

	public void updateOrderStatusSuccess(List<RouteLocation> routeLocations, Vehicle vehicle) {
		int pickupSeq = 10;
		for (RouteLocation routeLocation : routeLocations) {
			Criteria criteria = Criteria.where("orderId").is(routeLocation.orderId);
			Query query = new Query(criteria);
			Update update = new Update();
			update.set("orderStatus", 4);
			update.set("phleboId", vehicle.id);
			update.set("firstName", vehicle.firstName);
			update.set("lastName", vehicle.lastName);
			update.set("mobileNumber", vehicle.mobileNumber);
			update.set("pickupSeq", pickupSeq);
			update.set("distanceFromPrevNode", routeLocation.distanceFromPrevNode);
			update.set("distanceSoFar", routeLocation.distanceSoFar);
			update.set("reachTime", routeLocation.reachTime);
			mongoTemplate.findAndModify(query, update, Map.class,"LgpOrder");
			pickupSeq = pickupSeq + 10;
		}
	}

	public void updateOrderStatusFailure(List<String> orderIds) {
		Criteria criteria = Criteria.where("orderId").in(orderIds);
		Query query = new Query(criteria);
		Update update = new Update();
		update.set("orderStatus", 11);
		update.set("rejectReason", "Not able to assign within this schedule time");
		mongoTemplate.updateMulti(query, update, "LgpOrder");
	}
}

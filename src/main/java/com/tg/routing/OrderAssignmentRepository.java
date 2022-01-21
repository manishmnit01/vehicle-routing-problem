package com.tg.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Map;

@Repository
public class OrderAssignmentRepository {

	@Autowired
	private MongoTemplate mongoTemplate;

	void insert(OrderAssignment orderAssignment) {
		mongoTemplate.insert(orderAssignment);
	}

	/*void update(String assignmentId, OrderAssignmentStatus status, int assigned, int dropped, String failedMessage) {

		Criteria criteria = Criteria.where("_id").is(assignmentId);
		Query query = new Query(criteria);
		Update update = new Update();
		update.set("status", status);
		update.set("assigned", assigned);
		update.set("dropped", dropped);
		update.set("failedMessage", failedMessage);
		update.set("updatedTime", new Date());
		mongoTemplate.findAndModify(query, update, Map.class,"OrderAssignments");
	}*/

	public OrderAssignment update(OrderAssignment orderAssignment) {

		Criteria criteria = Criteria.where("_id").is(orderAssignment.assignmentId);
		Query query = new Query(criteria);
		Update update = new Update();
		update.set("status", orderAssignment.status);
		update.set("total", orderAssignment.total);
		update.set("assigned", orderAssignment.assigned);
		update.set("dropped", orderAssignment.dropped);
		update.set("failedMessage", orderAssignment.failedMessage);
		update.set("updatedTime", new Date());
		return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), OrderAssignment.class,"OrderAssignments");
	}
}

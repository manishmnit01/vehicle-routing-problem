package com.tg.routing;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "OrderAssignments")
public class OrderAssignment {

	@Id
	public String assignmentId;

	public OrderAssignmentStatus status;

	public String failedMessage;

	public int total;

	public int assigned;

	public int dropped;

	public Date createdTime = new Date();

	public Date updatedTime = new Date();
}

package com.tg.vehicleroutingv2;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("LgpOrder")
public class LgpOrder {

	@Id
	public String orderId;

	@Field("order_type")
	public int orderType;

	@Field("zone_id")
	public String zoneId;

	@Field("ship_to_latitude")
	public double latitude;

	@Field("ship_to_longitude")
	public double longitude;

	public int startTimeMinutes;

	public int endTimeMinutes;

	public int routeId;
}

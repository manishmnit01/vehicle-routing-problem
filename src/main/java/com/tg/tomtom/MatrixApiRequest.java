package com.tg.tomtom;

import java.util.List;

public class MatrixApiRequest {

	public List<SinglePoint> origins;

	public List<SinglePoint> destinations;

	public MatrixApiRequest(List<SinglePoint> origins, List<SinglePoint> destinations) {
		this.origins = origins;
		this.destinations = destinations;
	}
}

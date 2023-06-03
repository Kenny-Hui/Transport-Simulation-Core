package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.generated.PlatformSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.*;

public final class Platform extends PlatformSchema {

	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final IntAVLTreeSet routeColors = new IntAVLTreeSet();
	private final Long2ObjectOpenHashMap<Angle> anglesFromDepot = new Long2ObjectOpenHashMap<>();

	public Platform(Position position1, Position position2, TransportMode transportMode, Simulator simulator) {
		super(position1, position2, transportMode, simulator);
	}

	public Platform(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
		updateData(readerBase);
		DataFixer.unpackPlatformDwellTime(readerBase, value -> dwellTime = value);
	}

	public void setDwellTime(long dwellTime) {
		this.dwellTime = dwellTime;
	}

	public long getDwellTime() {
		return transportMode.continuousMovement ? 1 : Math.max(1, dwellTime);
	}

	public void setAngles(long depotId, Angle angle) {
		anglesFromDepot.put(depotId, angle);
	}

	public JsonObject getOBAStopElement(IntArraySet routesUsed) {
		final JsonArray jsonArray = new JsonArray();
		routeColors.forEach(color -> {
			jsonArray.add(Utilities.numberToPaddedHexString(color, 6));
			routesUsed.add(color);
		});

		Angle angle = null;
		for (final Angle checkAngle : anglesFromDepot.values()) {
			if (angle == null) {
				angle = checkAngle;
			} else if (angle != checkAngle) {
				angle = null;
				break;
			}
		}
		final String angleString = angle == null ? "" : angle.toString();

		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("code", getHexId());
		jsonObject.addProperty("direction", angleString.length() == 3 ? angleString.substring(2) : angleString);
		jsonObject.addProperty("id", getHexId());
		final LatLon latLon = new LatLon(getMidPosition());
		jsonObject.addProperty("lat", latLon.lat);
		jsonObject.addProperty("locationType", 0);
		jsonObject.addProperty("lon", latLon.lon);
		final String stationName = area == null ? "" : Utilities.formatName(area.getName());
		jsonObject.addProperty("name", String.format("%s%s%s%s", stationName, !stationName.isEmpty() && !name.isEmpty() ? " - " : "", name.isEmpty() ? "" : "Platform ", name));
		jsonObject.add("routeIds", jsonArray);
		jsonObject.addProperty("wheelchairBoarding", "UNKNOWN");

		return jsonObject;
	}
}

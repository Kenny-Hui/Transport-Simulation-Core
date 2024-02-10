package org.mtr.core.servlet;

import org.mtr.core.integration.Integration;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.EnumHelper;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.Locale;

public final class IntegrationServlet extends ServletBase {

	public IntegrationServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator) {
		final IntegrationResponse integrationResponse = new IntegrationResponse(data, parameters, new Integration(jsonReader, simulator), currentMillis, simulator);
		switch (EnumHelper.valueOf(Operation.UPDATE, endpoint.toUpperCase(Locale.ROOT))) {
			case UPDATE:
				return Utilities.getJsonObjectFromData(integrationResponse.update());
			case GET:
				return Utilities.getJsonObjectFromData(integrationResponse.get());
			case DELETE:
				return Utilities.getJsonObjectFromData(integrationResponse.delete());
			case GENERATE:
				return Utilities.getJsonObjectFromData(integrationResponse.generate());
			case CLEAR:
				return Utilities.getJsonObjectFromData(integrationResponse.clear());
			case LIST:
				return Utilities.getJsonObjectFromData(integrationResponse.list());
			default:
				return new JsonObject();
		}
	}

	public enum Operation {
		UPDATE, GET, DELETE, GENERATE, CLEAR, LIST;

		public String getEndpoint() {
			return toString().toLowerCase(Locale.ENGLISH);
		}
	}
}

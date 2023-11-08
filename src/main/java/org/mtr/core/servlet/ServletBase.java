package org.mtr.core.servlet;

import org.mtr.core.integration.Response;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.JsonWriter;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.io.netty.handler.codec.http.HttpResponseStatus;
import org.mtr.libraries.io.netty.handler.codec.http.QueryStringDecoder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class ServletBase<T> {

	protected ServletBase(Webserver webserver, String path, Function<JsonReader, T> bodyInstance, ObjectImmutableList<Simulator> simulators) {
		webserver.addHttpListener(path, (queryStringDecoder, bodyObject, sendResponse) -> {
			final long currentMillis = System.currentTimeMillis();
			final T body = bodyInstance.apply(new JsonReader(bodyObject));

			if (tryGetParameter(queryStringDecoder, "dimensions").equals("all")) {
				simulators.forEach(simulator -> run(path, queryStringDecoder, body, null, currentMillis, simulator));
				buildResponseObject(sendResponse, currentMillis, null, HttpResponseStatus.OK);
			} else {
				int dimension = 0;
				try {
					dimension = Integer.parseInt(tryGetParameter(queryStringDecoder, "dimension"));
				} catch (Exception ignored) {
				}

				if (dimension < 0 || dimension >= simulators.size()) {
					buildResponseObject(sendResponse, currentMillis, null, HttpResponseStatus.BAD_REQUEST, "Invalid Dimension");
				} else {
					run(path, queryStringDecoder, body, sendResponse, currentMillis, simulators.get(dimension));
				}
			}
		});
	}

	@Nullable
	protected abstract JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, T body, long currentMillis, Simulator simulator);

	private void run(String path, QueryStringDecoder queryStringDecoder, T body, @Nullable BiConsumer<JsonObject, HttpResponseStatus> sendResponse, long currentMillis, Simulator simulator) {
		final String endpoint;
		final String data;
		final String extraPath = queryStringDecoder.path().replace(path, "");
		if (!extraPath.isEmpty()) {
			final String[] pathSplit = extraPath.substring(1).split("\\.")[0].split("/");
			endpoint = pathSplit.length > 3 ? pathSplit[3] : "";
			data = pathSplit.length > 4 ? pathSplit[4] : "";
		} else {
			endpoint = "";
			data = "";
		}
		final Object2ObjectAVLTreeMap<String, String> parameters = new Object2ObjectAVLTreeMap<>();
		queryStringDecoder.parameters().forEach((key, values) -> {
			if (!values.isEmpty()) {
				parameters.put(key, values.get(0));
			}
		});
		simulator.run(() -> {
			final JsonObject jsonObject = getContent(endpoint, data, parameters, body, currentMillis, simulator);
			if (sendResponse != null) {
				buildResponseObject(sendResponse, currentMillis, jsonObject, jsonObject == null ? HttpResponseStatus.NOT_FOUND : HttpResponseStatus.OK, endpoint, data);
			}
		});
	}

	private static <T> void buildResponseObject(BiConsumer<JsonObject, HttpResponseStatus> sendResponse, long currentMillis, @Nullable JsonObject data, HttpResponseStatus httpResponseStatus, String... parameters) {
		final StringBuilder reasonPhrase = new StringBuilder(httpResponseStatus.reasonPhrase());
		final String trimmedParameters = Arrays.stream(parameters).filter(parameter -> !parameter.isEmpty()).collect(Collectors.joining(", "));
		if (!trimmedParameters.isEmpty()) {
			reasonPhrase.append(" - ").append(trimmedParameters);
		}

		final Response response = new Response(httpResponseStatus.code(), currentMillis, reasonPhrase.toString());
		final JsonObject jsonObject = new JsonObject();
		response.serializeData(new JsonWriter(jsonObject));
		if (data != null) {
			jsonObject.add("data", data);
		}
		sendResponse.accept(jsonObject, httpResponseStatus);
	}

	private static String tryGetParameter(QueryStringDecoder queryStringDecoder, String parameter) {
		return queryStringDecoder.parameters().getOrDefault(parameter, Collections.singletonList("")).get(0);
	}
}

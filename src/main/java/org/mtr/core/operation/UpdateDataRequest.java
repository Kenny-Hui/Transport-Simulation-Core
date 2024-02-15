package org.mtr.core.operation;

import org.mtr.core.data.*;
import org.mtr.core.generated.operation.UpdateDataRequestSchema;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class UpdateDataRequest extends UpdateDataRequestSchema {

	private final Data data;

	public UpdateDataRequest(Data data) {
		this.data = data;
	}

	public UpdateDataRequest(ReaderBase readerBase, Data data) {
		super(readerBase);
		this.data = data;
		updateData(readerBase);
	}

	@Nonnull
	@Override
	protected Data stationsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data platformsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data sidingsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data routesDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data depotsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data liftsDataParameter() {
		return data;
	}

	public UpdateDataRequest addStation(Station station) {
		stations.add(station);
		return this;
	}

	public UpdateDataRequest addPlatform(Platform platform) {
		platforms.add(platform);
		return this;
	}

	public UpdateDataRequest addSiding(Siding siding) {
		sidings.add(siding);
		return this;
	}

	public UpdateDataRequest addRoute(Route route) {
		routes.add(route);
		return this;
	}

	public UpdateDataRequest addDepot(Depot depot) {
		depots.add(depot);
		return this;
	}

	public UpdateDataRequest addLift(Lift lift) {
		lifts.add(lift);
		return this;
	}

	public UpdateDataRequest addRail(Rail rail) {
		rails.add(rail);
		return this;
	}

	public UpdateDataRequest addSignalModification(SignalModification signalModification) {
		signalModifications.add(signalModification);
		return this;
	}

	public JsonObject update() {
		final UpdateDataResponse updateDataResponse = new UpdateDataResponse(data);

		stations.forEach(station -> update(station, true, data.stationIdMap.get(station.getId()), data.stations, updateDataResponse.getStations()));
		platforms.forEach(platform -> update(platform, false, data.platformIdMap.get(platform.getId()), data.platforms, updateDataResponse.getPlatforms()));
		sidings.forEach(siding -> update(siding, false, data.sidingIdMap.get(siding.getId()), data.sidings, updateDataResponse.getSidings()));
		routes.forEach(route -> update(route, true, data.routeIdMap.get(route.getId()), data.routes, updateDataResponse.getRoutes()));
		depots.forEach(depot -> update(depot, true, data.depotIdMap.get(depot.getId()), data.depots, updateDataResponse.getDepots()));
		lifts.forEach(lift -> {
			final ObjectArrayList<Lift> liftsToModify = new ObjectArrayList<>();
			data.lifts.removeIf(existingLift -> {
				if (lift.overlappingFloors(existingLift)) {
					liftsToModify.add(existingLift);
					return true;
				} else {
					return false;
				}
			});
			update(lift, true, liftsToModify.isEmpty() ? null : liftsToModify.get(0), data.lifts, updateDataResponse.getLifts());
		});
		rails.forEach(rail -> update(rail, false, data.railIdMap.get(rail.getHexId()), data.rails, updateDataResponse.getRails()));
		signalModifications.forEach(signalModification -> signalModification.applyModificationToRail(data, updateDataResponse.getRails()));

		final ObjectArrayList<Siding> sidingsToInit = new ObjectArrayList<>();
		updateDataResponse.getRails().forEach(rail -> rail.checkOrCreateSavedRail(data, updateDataResponse.getPlatforms(), sidingsToInit));
		data.sync();
		sidingsToInit.forEach(Siding::init);
		updateDataResponse.getSidings().addAll(sidingsToInit);

		updateDataResponse.getStations().forEach(station -> station.savedRails.forEach(platform -> platform.routes.forEach(route -> SimplifiedRoute.addToList(updateDataResponse.getSimplifiedRoutes(), route))));
		updateDataResponse.getPlatforms().forEach(platform -> platform.routes.forEach(route -> SimplifiedRoute.addToList(updateDataResponse.getSimplifiedRoutes(), route)));
		updateDataResponse.getRoutes().forEach(route -> SimplifiedRoute.addToList(updateDataResponse.getSimplifiedRoutes(), route));

		return Utilities.getJsonObjectFromData(updateDataResponse);
	}

	private static <T extends SerializedDataBase> void update(T newData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectArrayList<T> dataToUpdate) {
		final boolean isRail = newData instanceof Rail;
		final boolean isValid = !isRail || ((Rail) newData).isValid();

		if (existingData == null) {
			if (addNewData && isValid) {
				dataSet.add(newData);
				dataToUpdate.add(newData);
			}
		} else if (isValid) {
			// For AVL tree sets, data must be removed and re-added when modified
			dataSet.remove(existingData);
			if (isRail) {
				dataSet.add(newData);
				dataToUpdate.add(newData);
			} else {
				existingData.updateData(new JsonReader(Utilities.getJsonObjectFromData(newData)));
				dataSet.add(existingData);
				dataToUpdate.add(existingData);
			}
		}
	}
}

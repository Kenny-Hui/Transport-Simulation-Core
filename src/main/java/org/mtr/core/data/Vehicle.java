package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.generated.VehicleSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public final class Vehicle extends VehicleSchema {

	private boolean doorTarget;
	private double doorValue;
	private int manualNotch;

	public final double railLength;
	public final ObjectImmutableList<VehicleCar> vehicleCars;
	public final double totalVehicleLength;
	public final int vehicleCarCount;

	public final ObjectImmutableList<PathData> path;
	public final int repeatIndex1;
	public final int repeatIndex2;

	public final double acceleration;
	public final boolean isManualAllowed;
	public final double maxManualSpeed;
	public final long manualToAutomaticTime;

	private final Siding siding;
	private final double totalDistance;
	private final double defaultPosition;

	public static final double ACCELERATION_DEFAULT = 1D / 250000;
	public static final double MAX_ACCELERATION = 1D / 50000;
	public static final double MIN_ACCELERATION = 1D / 2500000;
	public static final int DOOR_MOVE_TIME = 64;
	private static final int DOOR_DELAY = 20;

	public Vehicle(
			Siding siding, Simulator simulator, TransportMode transportMode, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime
	) {
		super(transportMode, simulator);

		this.siding = siding;
		this.railLength = Siding.getRailLength(railLength);

		this.vehicleCars = new ObjectImmutableList<>(vehicleCars);
		vehicleCarCount = this.vehicleCars.size();
		this.totalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);

		path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		repeatIndex1 = pathSidingToMainRoute.size();
		repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;

		this.acceleration = roundAcceleration(acceleration);
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;
		totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).getEndDistance();
		defaultPosition = (this.railLength + totalVehicleLength) / 2;
	}

	public Vehicle(
			Siding siding, Simulator simulator, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, ReaderBase readerBase
	) {
		super(readerBase, simulator);

		this.siding = siding;
		this.railLength = Siding.getRailLength(railLength);

		this.vehicleCars = new ObjectImmutableList<>(vehicleCars);
		vehicleCarCount = this.vehicleCars.size();
		this.totalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);

		path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		repeatIndex1 = pathSidingToMainRoute.size();
		repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;

		this.acceleration = roundAcceleration(acceleration);
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;
		totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).getEndDistance();
		defaultPosition = (this.railLength + totalVehicleLength) / 2;

		updateData(readerBase);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public boolean getIsOnRoute() {
		return railProgress > defaultPosition;
	}

	public boolean closeToDepot() {
		return !getIsOnRoute() || railProgress < totalVehicleLength + railLength;
	}

	public boolean isCurrentlyManual() {
		return isCurrentlyManual;
	}

	public boolean changeManualSpeed(boolean isAccelerate) {
		if (doorValue == 0 && isAccelerate && manualNotch >= -2 && manualNotch < 2) {
			manualNotch++;
			return true;
		} else if (!isAccelerate && manualNotch > -2) {
			manualNotch--;
			return true;
		} else {
			return false;
		}
	}

	public boolean toggleDoors() {
		if (speed == 0) {
			doorTarget = !doorTarget;
			manualNotch = -2;
			return true;
		} else {
			doorTarget = false;
			return false;
		}
	}

	public double getRailSpeed(int railIndex) {
		final Rail thisRail = path.get(railIndex).getRail();
		final double railSpeed;
		if (thisRail.canAccelerate()) {
			railSpeed = thisRail.speedLimitMetersPerMillisecond;
		} else {
			final Rail lastRail = railIndex > 0 ? path.get(railIndex - 1).getRail() : thisRail;
			railSpeed = Math.max(lastRail.canAccelerate() ? lastRail.speedLimitMetersPerMillisecond : transportMode.defaultSpeedMetersPerMillisecond, speed);
		}
		return railSpeed;
	}

	public void writeVehiclePositions(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		writeVehiclePositions(Utilities.getIndexFromConditionalList(path, railProgress), vehiclePositions);
	}

	public void writeVehiclePositions(int currentIndex, Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		if (getIsOnRoute() && currentIndex >= 0) {
			int index = currentIndex;
			while (true) {
				final PathData pathData = path.get(index);
				final double start = Math.max(pathData.getStartDistance(), railProgress - totalVehicleLength);
				final double end = Math.min(pathData.getEndDistance(), railProgress);
				if (end - start > 0.01) {
					DataCache.put(vehiclePositions, pathData.getOrderedPosition1(), pathData.getOrderedPosition2(), vehiclePosition -> {
						final VehiclePosition newVehiclePosition = vehiclePosition == null ? new VehiclePosition() : vehiclePosition;
						newVehiclePosition.addSegment(pathData.reversePositions ? end : start, pathData.reversePositions ? start : end, id);
						return newVehiclePosition;
					}, Object2ObjectAVLTreeMap::new);
				}
				index--;
				if (index < 0 || railProgress - totalVehicleLength >= pathData.getStartDistance()) {
					break;
				}
			}
		}
	}

	public void simulateTrain(long millisElapsed, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, Long2LongAVLTreeMap vehicleTimesAlongRoute) {
		try {
			if (nextStoppingIndex >= path.size()) {
				railProgress = defaultPosition;
				return;
			}

			final boolean tempDoorTarget;
			final double tempDoorValue;

			if (!getIsOnRoute()) {
				railProgress = defaultPosition;
				reversed = false;
				tempDoorTarget = false;
				tempDoorValue = 0;
				speed = 0;
				nextStoppingIndex = 0;
				departureIndex = -1;

				if (isCurrentlyManual && manualNotch > 0) {
					startUp(-1);
				}
			} else {
				final double newAcceleration = acceleration * millisElapsed;
				final int currentIndex = Utilities.getIndexFromConditionalList(path, railProgress);

				if (repeatIndex2 == 0 && railProgress >= totalDistance - (railLength - totalVehicleLength) / 2 || !isManualAllowed && departureIndex < 0) {
					railProgress = defaultPosition;
					manualNotch = -2;
					ridingEntities.clear();
					tempDoorTarget = false;
					tempDoorValue = 0;
				} else {
					if (speed <= 0) {
						speed = 0;

						final PathData currentPathData = currentIndex > 0 ? path.get(currentIndex - 1) : null;
						final PathData nextPathData = path.get(repeatIndex2 > 0 && currentIndex >= repeatIndex2 ? repeatIndex1 : currentIndex);
						final boolean isOpposite = currentPathData != null && currentPathData.isOppositeRail(nextPathData);
						final boolean railClear = railBlockedDistance(currentIndex, nextPathData.getStartDistance() + (isOpposite ? totalVehicleLength : 0), 0, vehiclePositions) < 0;
						final long totalDwellMillis = currentPathData == null ? 0 : currentPathData.getDwellTime();

						if (totalDwellMillis == 0) {
							tempDoorTarget = false;
						} else {
							if (elapsedDwellTime + millisElapsed < totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY || railClear) {
								elapsedDwellTime += millisElapsed;
							}
							tempDoorTarget = openDoors();
						}

						if ((isCurrentlyManual || elapsedDwellTime >= totalDwellMillis) && railClear && (!isCurrentlyManual || manualNotch > 0)) {
							railProgress = nextPathData.getStartDistance();
							if (isOpposite) {
								railProgress += totalVehicleLength;
								reversed = !reversed;
							}
							startUp(departureIndex);
						}
					} else {
						final double safeStoppingDistance = 0.5 * speed * speed / acceleration;
						final double stoppingPoint;
						final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions);
						if (railBlockedDistance < 0) {
							stoppingPoint = path.get((int) nextStoppingIndex).getEndDistance();
						} else {
							stoppingPoint = railBlockedDistance + railProgress;
						}
						final double stoppingDistance = stoppingPoint - railProgress;

						if (!transportMode.continuousMovement && stoppingDistance < safeStoppingDistance) {
							speed = stoppingDistance <= 0 ? ACCELERATION_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, ACCELERATION_DEFAULT);
							manualNotch = -3;
						} else {
							if (manualNotch < -2) {
								manualNotch = 0;
							}
							if (isCurrentlyManual) {
								speed = Utilities.clamp(speed + manualNotch * newAcceleration / 2, 0, maxManualSpeed);
							} else {
								final double railSpeed = getRailSpeed(currentIndex);
								if (speed < railSpeed) {
									speed = Math.min(speed + newAcceleration, railSpeed);
									manualNotch = 2;
								} else if (speed > railSpeed) {
									speed = Math.max(speed - newAcceleration, railSpeed);
									manualNotch = -2;
								} else {
									manualNotch = 0;
								}
							}
						}

						tempDoorTarget = transportMode.continuousMovement && openDoors();

						railProgress += speed * millisElapsed;
						if (!transportMode.continuousMovement && railProgress >= stoppingPoint) {
							railProgress = stoppingPoint;
							speed = 0;
							manualNotch = -2;
						}
					}

					tempDoorValue = Utilities.clamp(doorValue + (double) (millisElapsed * (doorTarget ? 1 : -1)) / DOOR_MOVE_TIME, 0, 1);
				}

				writeVehiclePositions(currentIndex, vehiclePositions.get(1));
			}

			vehicleTimesAlongRoute.put(departureIndex, Math.round(siding.getTimeAlongRoute(railProgress)) + (long) elapsedDwellTime);

			doorTarget = tempDoorTarget;
			doorValue = tempDoorValue;
			if (doorTarget || doorValue != 0) {
				manualNotch = -2;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startUp(long newDepartureIndex) {
		departureIndex = newDepartureIndex;
		railProgress += ACCELERATION_DEFAULT;
		elapsedDwellTime = 0;
		speed = ACCELERATION_DEFAULT;
		doorTarget = false;
		doorValue = 0;
		nextStoppingIndex = path.size() - 1;
		for (int i = Utilities.getIndexFromConditionalList(path, railProgress); i < path.size(); i++) {
			if (path.get(i).getDwellTime() > 0) {
				nextStoppingIndex = i;
				break;
			}
		}
	}

	public long getDepartureIndex() {
		return departureIndex;
	}

	public boolean openDoors() {
		return doorTarget;
	}

	private double railBlockedDistance(int currentIndex, double checkRailProgress, double checkDistance, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions) {
		int index = currentIndex;
		while (true) {
			final PathData pathData = path.get(index);
			if (Utilities.isIntersecting(pathData.getStartDistance(), pathData.getEndDistance(), checkRailProgress, checkDistance + checkDistance)) {
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = DataCache.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						return vehiclePosition.isBlocked(
								id,
								pathData.reversePositions ? pathData.getEndDistance() - checkRailProgress - checkDistance : checkRailProgress - pathData.getStartDistance(),
								pathData.reversePositions ? pathData.getEndDistance() - checkRailProgress : checkRailProgress + checkDistance - pathData.getStartDistance()
						);
					}
				}
			}
			index++;
			if (index >= path.size()) {
				return -1;
			}
		}
	}

	public static double roundAcceleration(double acceleration) {
		final double tempAcceleration = Utilities.round(acceleration, 8);
		return tempAcceleration <= 0 ? ACCELERATION_DEFAULT : Utilities.clamp(tempAcceleration, MIN_ACCELERATION, MAX_ACCELERATION);
	}

	private static ObjectImmutableList<PathData> createPathData(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, boolean repeatInfinitely, PathData defaultPathData) {
		final ObjectArrayList<PathData> tempPath = new ObjectArrayList<>();
		if (pathSidingToMainRoute.isEmpty() || pathMainRoute.isEmpty() || !repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			tempPath.add(defaultPathData);
		} else {
			tempPath.addAll(pathSidingToMainRoute);
			tempPath.addAll(pathMainRoute);
			if (repeatInfinitely) {
				final PathData firstPathData = pathMainRoute.get(0);
				final PathData lastPathData = Utilities.getElement(pathMainRoute, -1);
				tempPath.add(new PathData(firstPathData, lastPathData.getStartDistance(), lastPathData.getStartDistance() + firstPathData.getEndDistance() - firstPathData.getStartDistance()));
			} else {
				tempPath.addAll(pathMainRouteToSiding);
			}
		}
		return new ObjectImmutableList<>(tempPath);
	}
}

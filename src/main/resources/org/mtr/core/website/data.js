import {pushIfNotExists, ROUTE_TYPES, setIfUndefined} from "./utilities.js";
import SETTINGS from "./settings.js";

const url = `${document.location.origin}${document.location.pathname.replace("index.html", "")}mtr/api/data/`;
let routes = [];

export function getData(callback) {
	fetch(url + "stations-and-routes").then(data => data.json()).then(({data}) => {
		routes = [];
		const availableRouteTypes = [];

		data.routes.forEach(route => {
			const stationIds = [];
			const routeStationsInfo = [];
			pushIfNotExists(availableRouteTypes, route.type);

			route.stations.forEach(routeStation => {
				const station = data.stations.find(station => station.id === routeStation.id);
				if (station !== undefined) {
					pushIfNotExists(stationIds, routeStation.id);
					routeStationsInfo.push({station, x: routeStation.x, y: routeStation.y, z: routeStation.z});
				}
			});

			if (stationIds.length > 1) {
				route.stations = routeStationsInfo;
				routes.push(route);
			}
		});

		SETTINGS.routeTypes = {};
		const allRouteTypes = Object.keys(ROUTE_TYPES);
		for (let i = 0; i < allRouteTypes.length; i++) {
			const routeType = allRouteTypes[i];
			if (availableRouteTypes.includes(routeType)) {
				SETTINGS.routeTypes[routeType] = i === 0 ? 1 : 0;
			}
		}

		updateData(callback);
	});
}

export function updateData(callback) {
	const stations = [];
	const routeTypes = [];

	routes.forEach(route => {
		const routeTypeSelected = SETTINGS.routeTypes[route.type] > 0;
		route.stations.forEach(({station, x, y, z}) => {
			if (routeTypeSelected) {
				if (!Array.isArray(station.x) || !Array.isArray(station.y) || !Array.isArray(station.z)) {
					station.x = [];
					station.y = [];
					station.z = [];
				}
				station.x.push(x);
				station.y.push(y);
				station.z.push(z);
				setIfUndefined(station.routes, () => station.routes = {});
				setIfUndefined(station.groups, () => station.groups = {});
				setIfUndefined(station.types, () => station.types = []);
				pushIfNotExists(stations, station);
			}

			setIfUndefined(station.types, () => station.types = []);
			pushIfNotExists(station.types, route.type);
			pushIfNotExists(routeTypes, route.type);
		});
	});

	stations.forEach(station => {
		const setSum = key => station[key] = station[key].reduce((previousTotal, currentValue) => previousTotal + currentValue, 0) / station[key].length;
		setSum("x");
		setSum("y");
		setSum("z");
	});

	const lineConnectionsOneWay = {};
	const getKeyAndReverseFromStationIds = (stationId1, stationId2) => {
		const reverse = stationId1 > stationId2;
		return [`${reverse ? stationId2 : stationId1}_${reverse ? stationId1 : stationId2}`, reverse];
	};

	routes.forEach(route => {
		if (SETTINGS.routeTypes[route.type] > 0) {
			const iterateStations = iterateForwards => {
				const routeStations = route.stations;
				for (let i = 0; i < routeStations.length; i++) {
					const index = iterateForwards ? i : routeStations.length - i - 1;
					const currentStation = routeStations[index].station;
					const station1Data = routeStations[index + (iterateForwards ? -1 : 1)];
					const station2Data = routeStations[index + (iterateForwards ? 1 : -1)];
					const station1 = station1Data === undefined ? currentStation : station1Data.station;
					const station2 = station2Data === undefined ? currentStation : station2Data.station;
					const colorAndType = `${route.color}|${route.type}`;

					setIfUndefined(currentStation.routes[colorAndType], () => currentStation.routes[colorAndType] = []);
					currentStation.routes[colorAndType].push((Math.round(Math.atan2(station2.x - station1.x, station2.z - station1.z) * 4 / Math.PI) + 8) % 4);

					if (station2Data !== undefined) {
						const nextStationId = station2.id;
						if (nextStationId !== currentStation.id) {
							setIfUndefined(currentStation.groups[nextStationId], () => currentStation.groups[nextStationId] = []);
							pushIfNotExists(currentStation.groups[nextStationId], colorAndType);
							if (iterateForwards) {
								const [key, reverse] = getKeyAndReverseFromStationIds(currentStation.id, nextStationId);
								const newKey = `${key}_${colorAndType}`;
								setIfUndefined(lineConnectionsOneWay[newKey], () => lineConnectionsOneWay[newKey] = {});
								lineConnectionsOneWay[newKey][reverse ? "backwards" : "forwards"] = true;
							}
						}
					}
				}
			};

			iterateStations(true);
			iterateStations(false);
		}
	});

	const lineConnections = {};
	const stationConnections = {};
	let maxLineConnectionLength = 0;
	let closestDistance;
	let centerX = 0;
	let centerY = 0;

	stations.forEach(station => {
		const combinedGroups = [];
		Object.values(station.groups).forEach(routeColors1 => {
			const combinedGroup = [...routeColors1];
			Object.values(station.groups).forEach(routeColors2 => {
				if (combinedGroup.some(routeColor => routeColors2.includes(routeColor))) {
					routeColors2.forEach(routeColor => pushIfNotExists(combinedGroup, routeColor));
				}
			});
			const groupToAddTo = combinedGroups.find(existingCombinedGroup => existingCombinedGroup.some(routeColor => combinedGroup.includes(routeColor)));
			if (groupToAddTo === undefined) {
				combinedGroups.push(combinedGroup);
			} else {
				combinedGroup.forEach(routeColor => pushIfNotExists(groupToAddTo, routeColor));
			}
		});

		const testRouteColors = [];
		combinedGroups.forEach(combinedGroup => combinedGroup.forEach(routeColor => {
			console.assert(!testRouteColors.includes(routeColor), "Duplicate colors in combined groups", combinedGroups);
			testRouteColors.push(routeColor);
		}));

		const routesForDirection = [[], [], [], []];
		combinedGroups.forEach(combinedGroup => {
			const directionsCount = [0, 0, 0, 0];
			combinedGroup.forEach(routeColor => station.routes[routeColor].forEach(direction => directionsCount[direction]++));
			let direction = 0;
			let max = 0;

			for (let i = 0; i < 4; i++) {
				if (directionsCount[i] > max) {
					direction = i;
					max = directionsCount[i];
				}
			}

			combinedGroup.forEach(routeColor => {
				routesForDirection[direction].push(routeColor);
				station.routes[routeColor] = direction;
			});
		});

		const rotate = routesForDirection[1].length + routesForDirection[3].length > routesForDirection[0].length + routesForDirection[2].length;
		Object.assign(station, {
			rotate,
			routeCount: Object.keys(station.routes).length,
			width: Math.max(Math.max(0, routesForDirection[rotate ? 1 : 0].length - 1), Math.max(0, routesForDirection[rotate ? 0 : 1].length - 1) * Math.SQRT1_2),
			height: Math.max(Math.max(0, routesForDirection[rotate ? 3 : 2].length - 1), Math.max(0, routesForDirection[rotate ? 2 : 3].length - 1) * Math.SQRT1_2),
		});

		routesForDirection.forEach(routesForOneDirection => routesForOneDirection.sort());

		Object.entries(station.groups).forEach(groupEntry => {
			const [stationId, routeColors] = groupEntry;
			const [key, reverse] = getKeyAndReverseFromStationIds(station.id, stationId)
			routeColors.sort();
			setIfUndefined(lineConnections[key], () => lineConnections[key] = {lineConnectionDetailsList: routeColors.map(() => Object.create(null))});
			const index = reverse ? 2 : 1;
			const direction = station.routes[routeColors[0]];
			const lineConnection = lineConnections[key];
			lineConnection[`direction${index}`] = direction;
			lineConnection[`x${index}`] = station.x;
			lineConnection[`z${index}`] = station.z;

			for (let i = 0; i < routeColors.length; i++) {
				const color = routeColors[i];
				const lineConnectionDetails = lineConnection.lineConnectionDetailsList[i];
				console.assert(lineConnectionDetails.color === undefined || lineConnectionDetails.color === color, "Color and offsets mismatch", lineConnectionDetails);
				lineConnectionDetails.color = color;
				lineConnectionDetails[`offset${index}`] = routesForDirection[direction].indexOf(color) - routesForDirection[direction].length / 2 + 0.5;
				const {forwards, backwards} = lineConnectionsOneWay[`${key}_${color}`];
				lineConnectionDetails.oneWay = forwards && backwards ? 0 : forwards ? 1 : -1;
			}

			if (lineConnection.hasOwnProperty("x1") && lineConnection.hasOwnProperty("z1") && lineConnection.hasOwnProperty("x2") && lineConnection.hasOwnProperty("z2")) {
				const lineConnectionLength = Math.abs(lineConnection.x2 - lineConnection.x1) + Math.abs(lineConnection.z2 - lineConnection.z1);
				lineConnection.length = lineConnectionLength;
				maxLineConnectionLength = Math.max(maxLineConnectionLength, lineConnectionLength);
			}
		});

		const distance = Math.abs(station.x) + Math.abs(station.z);
		if (closestDistance === undefined || distance < closestDistance) {
			closestDistance = distance;
			centerX = -station.x;
			centerY = -station.z;
		}

		if (station.hasOwnProperty("connections")) {
			station.connections.forEach(stationId => {
				const [key, reverse] = getKeyAndReverseFromStationIds(station.id, stationId);
				setIfUndefined(stationConnections[key], () => stationConnections[key] = {});
				const stationConnection = stationConnections[key];
				stationConnection[`x${reverse ? 2 : 1}`] = station.x;
				stationConnection[`z${reverse ? 2 : 1}`] = station.z;
			});
		}

		delete station.groups;
		delete station.routes;
	});

	stations.sort((station1, station2) => {
		if (station1.routeCount === station2.routeCount) {
			return station2.name.length - station1.name.length;
		} else {
			return station2.routeCount - station1.routeCount;
		}
	});

	callback(
		centerX,
		centerY,
		stations,
		routeTypes,
		Object.values(lineConnections).sort((lineConnection1, lineConnection2) => lineConnection2.length - lineConnection1.length),
		maxLineConnectionLength,
		Object.values(stationConnections).filter(stationConnection => stationConnection.hasOwnProperty("x1") && stationConnection.hasOwnProperty("z1") && stationConnection.hasOwnProperty("x2") && stationConnection.hasOwnProperty("z2")),
	);
}

package org.mtr.core.operation;

import org.mtr.core.data.Depot;
import org.mtr.core.generated.operation.GenerateByDepotNameSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;

import java.util.function.Consumer;

public final class GenerateByDepotName extends GenerateByDepotNameSchema {

	public GenerateByDepotName() {
	}

	public GenerateByDepotName(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public JsonObject generate(Simulator simulator, Consumer<JsonObject> sendResponse) {
		Depot.generateDepotsByName(simulator, filter, sendResponse);
		return new JsonObject();
	}
}

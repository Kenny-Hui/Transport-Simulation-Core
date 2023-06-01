package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.generated.AreaBaseSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public abstract class AreaBase<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> extends AreaBaseSchema {

	public final ObjectAVLTreeSet<U> savedRails = new ObjectAVLTreeSet<>();

	public AreaBase(TransportMode transportMode, Simulator simulator) {
		super(transportMode, simulator);
	}

	public AreaBase(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
		DataFixer.unpackAreaBasePositions(readerBase, (value1, value2) -> {
			position1 = value1;
			position2 = value2;
		});
	}

	@Override
	protected final Position getDefaultPosition1() {
		return new Position(0, 0, 0);
	}

	@Override
	protected final Position getDefaultPosition2() {
		return new Position(0, 0, 0);
	}

	@Override
	public boolean isValid() {
		return !name.isEmpty();
	}

	public void setCorners(Position position1, Position position2) {
		this.position1 = position1;
		this.position2 = position2;
	}

	public boolean inArea(Position position) {
		return validCorners(this) && Utilities.isBetween(position.getX(), position1.getX(), position2.getX()) && Utilities.isBetween(position.getY(), position1.getY(), position2.getY()) && Utilities.isBetween(position.getZ(), position1.getZ(), position2.getZ());
	}

	public boolean intersecting(AreaBase<T, U> areaBase) {
		return validCorners(this) && validCorners(areaBase) && (inThis(areaBase) || areaBase.inThis(this));
	}

	public Position getCenter() {
		return validCorners(this) ? new Position((position1.getX() + position2.getX()) / 2, (position1.getY() + position2.getY()) / 2, (position1.getZ() + position2.getZ()) / 2) : null;
	}

	private boolean inThis(AreaBase<T, U> areaBase) {
		final long x1 = areaBase.position1.getX();
		final long y1 = areaBase.position1.getY();
		final long z1 = areaBase.position1.getZ();
		final long x2 = areaBase.position2.getX();
		final long y2 = areaBase.position2.getY();
		final long z2 = areaBase.position2.getZ();
		return inArea(areaBase.position1) || inArea(areaBase.position2) || inArea(new Position(x1, y1, z2)) || inArea(new Position(x1, y2, z1)) || inArea(new Position(x1, y2, z2)) || inArea(new Position(x2, y1, z1)) || inArea(new Position(x2, y1, z2)) || inArea(new Position(x2, y2, z1));
	}

	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> boolean validCorners(AreaBase<T, U> areaBase) {
		return areaBase != null && areaBase.position1 != null && areaBase.position2 != null;
	}
}
